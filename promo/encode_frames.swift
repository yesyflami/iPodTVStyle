import Foundation
import AVFoundation
import CoreGraphics
import ImageIO

// encode_frames <frameDir> <out.mp4> <fps>
guard CommandLine.arguments.count >= 4 else {
    fputs("usage: encode_frames <dir> <out.mp4> <fps>\n", stderr)
    exit(2)
}
let frameDir = CommandLine.arguments[1]
let outPath = CommandLine.arguments[2]
let fps = Int32(CommandLine.arguments[3]) ?? 30

let names = try FileManager.default.contentsOfDirectory(atPath: frameDir)
    .filter { $0.lowercased().hasSuffix(".png") }
    .sorted()
guard let firstName = names.first else {
    fputs("no frames\n", stderr); exit(3)
}

func loadImage(_ path: String) -> CGImage? {
    guard let src = CGImageSourceCreateWithURL(URL(fileURLWithPath: path) as CFURL, nil) else { return nil }
    return CGImageSourceCreateImageAtIndex(src, 0, nil)
}

guard let firstImage = loadImage(frameDir + "/" + firstName) else {
    fputs("cannot read first frame\n", stderr); exit(4)
}
let width = firstImage.width
let height = firstImage.height
print("frames: \(names.count)  size: \(width)x\(height)  fps: \(fps)")

let outURL = URL(fileURLWithPath: outPath)
try? FileManager.default.removeItem(at: outURL)

let writer = try AVAssetWriter(outputURL: outURL, fileType: .mp4)
let settings: [String: Any] = [
    AVVideoCodecKey: AVVideoCodecType.h264,
    AVVideoWidthKey: width,
    AVVideoHeightKey: height,
    AVVideoCompressionPropertiesKey: [
        AVVideoAverageBitRateKey: 14_000_000,
        AVVideoProfileLevelKey: AVVideoProfileLevelH264HighAutoLevel,
        AVVideoMaxKeyFrameIntervalKey: 60,
        AVVideoAllowFrameReorderingKey: false
    ]
]
let input = AVAssetWriterInput(mediaType: .video, outputSettings: settings)
input.expectsMediaDataInRealTime = false
let adaptor = AVAssetWriterInputPixelBufferAdaptor(
    assetWriterInput: input,
    sourcePixelBufferAttributes: [
        kCVPixelBufferPixelFormatTypeKey as String: Int(kCVPixelFormatType_32BGRA),
        kCVPixelBufferWidthKey as String: width,
        kCVPixelBufferHeightKey as String: height,
        kCVPixelBufferMetalCompatibilityKey as String: true
    ])
writer.add(input)
guard writer.startWriting() else {
    fputs("startWriting failed: \(String(describing: writer.error))\n", stderr); exit(5)
}
writer.startSession(atSourceTime: .zero)

let colorSpace = CGColorSpaceCreateDeviceRGB()
let bitmapInfo = CGImageAlphaInfo.premultipliedFirst.rawValue
    | CGBitmapInfo.byteOrder32Little.rawValue

var appended = 0
for (index, name) in names.enumerated() {
    while !input.isReadyForMoreMediaData { usleep(2000) }
    guard let pool = adaptor.pixelBufferPool else {
        fputs("no pixel buffer pool\n", stderr); exit(6)
    }
    var bufferOut: CVPixelBuffer?
    CVPixelBufferPoolCreatePixelBuffer(nil, pool, &bufferOut)
    guard let buffer = bufferOut else { continue }

    CVPixelBufferLockBaseAddress(buffer, [])
    if let base = CVPixelBufferGetBaseAddress(buffer),
       let ctx = CGContext(data: base,
                           width: width, height: height,
                           bitsPerComponent: 8,
                           bytesPerRow: CVPixelBufferGetBytesPerRow(buffer),
                           space: colorSpace,
                           bitmapInfo: bitmapInfo) {
        // CGBitmapContext row 0 is the bottom of the frame, so a direct draw lands upright
        // in the pixel buffer; an extra flip would mirror the picture.
        ctx.interpolationQuality = .high
        if let image = loadImage(frameDir + "/" + name) {
            ctx.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
        }
    }
    CVPixelBufferUnlockBaseAddress(buffer, [])

    let time = CMTime(value: CMTimeValue(index), timescale: fps)
    if adaptor.append(buffer, withPresentationTime: time) {
        appended += 1
    } else if let error = writer.error {
        fputs("append failed at \(index): \(error)\n", stderr); exit(7)
    }
}

input.markAsFinished()
let done = DispatchSemaphore(value: 0)
writer.finishWriting { done.signal() }
done.wait()

if writer.status == .completed {
    let attrs = try? FileManager.default.attributesOfItem(atPath: outPath)
    let size = (attrs?[.size] as? NSNumber)?.intValue ?? 0
    print("ok: \(outPath)  frames=\(appended)  bytes=\(size)")
} else {
    fputs("finish failed: \(String(describing: writer.error))\n", stderr)
    exit(8)
}
