# 渲染原理

想改视觉之前建议先读这一页，它解释了代码里那些"看起来很奇怪"的常量是怎么来的。

渲染全部发生在 `CarouselView.onDraw()`：**一个 View、一块 Canvas、零依赖**。
没有 WebView，没有 OpenGL，没有第三方库。

---

## 1. 设计坐标空间

所有几何都在 **1920×1080 的设计坐标**里计算，最后等比缩放到实际屏幕并居中：

```java
float scale = Math.min(getWidth() / DESIGN_W, getHeight() / DESIGN_H);
canvas.translate(dx, dy);
canvas.scale(scale, scale);
drawScene(canvas);
```

好处：所有常量（卡片尺寸、间距、字号）都是"1920 宽下的像素"，跟实际分辨率无关。

---

## 2. 透视投影

摄像机参数照搬了 CSS 版的 `perspective: 1180px`：

```java
private static final float PERSPECTIVE = 1180f;   // 虚拟焦距
private static final float RAIL_Y = DESIGN_H * .43f;  // 卡片"站"在哪条水平线上
```

每个卡片的四个角单独投影：

```java
worldX = DESIGN_W/2 + tx + localX * scale * cos(θ);
worldY = RAIL_Y   + ty + localY * scale;
worldZ = tz - localX * scale * sin(θ);
projection = PERSPECTIVE / (PERSPECTIVE - worldZ);
screen = center + (world - center) * projection;
```

- `tx / ty / tz` 来自三条查表曲线 `X_FRACTIONS / Y_OFFSETS / Z_OFFSETS`，
  按**离中心的距离**（0…4）插值
- `θ` 来自 `ROTATIONS = {0, 58, 66, 72, 72, 72}` 度 —— 越远转得越侧
- 四角算完，用 `Matrix.setPolyToPoly()` 把预制位图映射到这个四边形上

**为什么不用 `Matrix.setCameraDistance` + 3D 变换？** 因为我们需要对每张卡片独立
控制"投影后四边形"，而且要在投影结果上做裁剪。自己算四角反而更简单、更可控。

---

## 3. 卡片是怎样贴上去的

N 个 app（N 就是这台电视上的应用数量）排成一个环，任意时刻每个卡片有一个
`position ∈ [-N/2, N/2]`：

```java
position = circularPosition(index) + motion;
```

`motion` 在切换动画里从 ±1 缓动到 0，所以卡片是"连续滑过去"，而不是瞬移。

绘制顺序 **按 |position| 从大到小** —— 最远的先画，中心最后画，天然形成层叠
（中心卡压住两侧邻居）。

---

## 4. 烘焙：把昂贵的东西只算一次

这是能在低端电视上跑顺的核心。**每帧只做贴图搬运，不做任何滤镜/模糊/阴影计算。**

### 卡片贴图（`app.card`，448×448）

1. 384×384 的卡片面：底色 → logo → 一道 115° 高光渐变 → 1px 内描边 → 圆角裁切
2. 复制到 448×448 画布中央，用 `Paint.setShadowLayer(22, 0, 13, 0x42000000)`
   画一层**卡片形状的阴影**，再把面盖上去

底色的采样、图标的 384 栅格化、以及"图标是不是整块铺满"的判断都在 `ensureCard()` 里
一次做完；栅格化后只保留 448 的贴图和两张倒影，中间那张 384 的卡片面烘焙完就回收。

应用不超过 15 个时全部在构造里烘好（和以前一样）；超过 15 个只烘当前窗口 ±4，
其余在划到的时候补 —— 每张约 0.9 MB，这样长列表也不会开局吃掉几十 MB。

32px 的边距就是给阴影留的。绘制时这个 448 的贴图整体参与投影 —— 所以阴影也跟着
一起被透视变换，不会喷在平面上。

### 倒影贴图（`app.reflection`，192×68）

1. 把卡片面**垂直翻转**并按 `1/2` 采样（半分辨率够用，模糊后看不出）
2. `boxBlur(radius=3, passes=3)` —— 三次盒式模糊近似高斯
3. `finishReflection()`：按 `(1-t)^1.9 × 0.58` 向下淡出，并把**背景色混进像素**，
   输出**完全不透明**的贴图

第 3 步是关键。倒影是不透明的，所以绘制时它天然会盖住之前画过的、更外侧卡片的
倒影 —— 于是"倒影互相叠色"这个 bug 从机制上消失了，不需要任何阈值判断。

代价：每个 app 存两份（浅色底 / 深色底），共 +1.8 MB。

---

## 5. 遮挡裁剪

卡片之间会互相覆盖。为了不让外侧卡片的阴影盖到中心卡片上，需要按"更靠中心
那张卡"的轮廓裁一刀：

```java
float near = Math.max(0f, absolute - 1f);
float nearPosition = near <= 0f ? 0f : Math.signum(position) * near;
fillProjectedQuad(nearPosition, ...)      // 算出邻居的四角
canvas.clipRect(boundary, 0, DESIGN_W, DESIGN_H);   // 只保留外侧那一半
```

两个容易踩的点：

1. **`|p| < 1` 时邻居就是中心卡本身**，所以要把 `near` 夹到 0。早期版本没夹，
   导致卡片滑过中心槽位时被裁成一条边 —— 表现是"中间闪过一个白方块"。
2. **卡片本体和倒影要用不同阈值**：本体 `> 1.02`，倒影 `>= 1`。
   本体在 0…1 之间是"正在穿过中心"的那张，必须完整保留；倒影则任何时候都得裁，
   否则会叠色。

---

## 6. 环首尾相接时的淡入淡出

可见窗口只有 9 个槽位（`|position| ≤ 4`，4→5 之间淡出），而环的长度等于应用数量。
所以环形回绕时卡片必须"瞬移"。
为了不闪，回绕发生的那一刻它必须是透明的：

```java
float edgeFade(float absolute) {
    if (absolute <= WRAP_DISTANCE - 1f) return 1f;   // ≤4
    return Math.max(0f, WRAP_DISTANCE - absolute);   // 4→5 线性淡出
}
```

另外在 `position` 超过 4 时，额外画一张"离场副本"在对面：

```java
float leaving = position - Math.signum(motion) * apps.length;
drawCard(canvas, app, leaving, Math.abs(motion));
```

于是视觉上是"一边滑出去一边淡出、另一边滑进来"，而不是闪现。

---

## 7. 顶部图标

三个按钮都是矢量绘制，但**齿轮是烘焙成位图的**，因为直接 `drawPath` 描边
在 28px 尺寸下会出现半透明灰边（齿根那段弧只有 4px 长）。

齿轮的烘焙流程：

```java
canvas.scale(4, 4);                    // 4× 超采样
canvas.drawPath(GEAR_PATH, stroke);    // 2.2px 描边（和 Wi-Fi 一致）
Bitmap scaled = createScaledBitmap(bmp, 40, 40, true);   // 先缩到显示尺寸
softenAlpha(scaled);                   // alpha^0.78 轻提对比，保留渐变
```

三条经验：

- 缩放**必须在烘焙阶段完成**，不能留给 `drawBitmap` —— 屏幕上的二次采样会重新
  产生灰边
- **不要做硬阈值**（`alpha>140→255`）：那样边缘会变成台阶，锯齿肉眼可见
- 线宽要和同类图标对齐（这里是 Wi-Fi 的 2.2px），否则一眼看出不是一套

---

## 8. 帧预算

实测（Skyworth 7S85 / Android 8 / 1080p）：

| 指标 | 数值 |
|---|---|
| 每帧绘制的贴图 | ~20 个（9 卡 + 9 倒影 + 偶尔 1 张离场副本 + 3 个图标） |
| 帧时间（中位数） | 6–7 ms |
| 帧时间（90 分位） | 11–15 ms |
| 掉帧率 | 7–8 % |

刷新节流在 `navigate()` 里：每 20 ms 触发一次 `invalidate()`。
试过 16 ms（60fps），结果掉帧率反而升到 21 % —— 这个 GPU 撑不住 60fps，
稳定的 50fps 观感更好。

### 性能教训

| 做过的事 | 代价 |
|---|---|
| 用 `drawPath` 给倒影铺一块背景色 | 单帧 17 ms（掉帧 51 %）→ 改成 1×1 位图贴四边形 |
| 动画中把贴图换成"无阴影版"省带宽 | 视觉 bug（阴影凭空消失），且省不到什么 |
| 让 `drawBitmap` 在屏幕上缩放 | 边缘灰圈，倒影/图标都中招 |

---

## 9. 想继续改的话

| 目标 | 从哪下手 |
|---|---|
| 更斜 / 更密 / 更大的卡片 | `X_FRACTIONS`、`ROTATIONS`、`SCALES`、`HTML_CARD` |
| 更快的切换 | `ANIMATION_MS`（现在 420 ms） |
| 倒影更明显 / 更淡 | `finishReflection()` 里的 `0.58f` 和 `FLOOR_LOSS` |
| 倒影更长 / 更短 | `REFL_H`（现在 68，半分辨率） |
| 换字体 | `loadNameFace()` / `loadFace()` |
| 深色主题配色 | `drawScene()` 顶部的几个颜色变量 |
