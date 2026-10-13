// macOS-only asset authoring tool. The generated PNGs are committed for CI.
import AppKit
import CoreText

let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
let width = 720, height = 460
for weight in ["Regular", "SemiBold", "Bold"] {
    let font = root.appendingPathComponent("src/assets/fonts/Pretendard-\(weight).otf")
    CTFontManagerRegisterFontsForURL(font as CFURL, .process, nil)
}
func color(_ hex: UInt32) -> NSColor {
    NSColor(srgbRed: CGFloat((hex >> 16) & 255) / 255,
            green: CGFloat((hex >> 8) & 255) / 255,
            blue: CGFloat(hex & 255) / 255, alpha: 1)
}
func text(_ value: String, _ x: CGFloat, _ y: CGFloat, _ size: CGFloat,
          _ weight: String, _ ink: UInt32, centered: Bool = false) {
    let font = NSFont(name: "Pretendard-\(weight)", size: size)!
    let attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color(ink)]
    let string = value as NSString
    let origin = centered ? x - string.size(withAttributes: attrs).width / 2 : x
    string.draw(at: NSPoint(x: origin, y: y), withAttributes: attrs)
}
func drawImage(_ image: NSImage, _ rect: NSRect) {
    image.draw(in: rect, from: .zero, operation: .sourceOver, fraction: 1, respectFlipped: true, hints: nil)
}
// macOS does not round arbitrary .icns/NSImage artwork for us. Preserve the
// existing illustration, with a transparent margin and the app logo's corners.
func renderMacIcon() throws {
    let bitmap = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: 1024,
        pixelsHigh: 1024, bitsPerSample: 8, samplesPerPixel: 4,
        hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
    let context = NSGraphicsContext(bitmapImageRep: bitmap)!.cgContext
    context.translateBy(x: 0, y: 1024)
    context.scaleBy(x: 1, y: -1)
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(cgContext: context, flipped: true)
    context.clear(CGRect(x: 0, y: 0, width: 1024, height: 1024))
    let tile = NSRect(x: 96, y: 96, width: 832, height: 832)
    let shape = NSBezierPath(roundedRect: tile, xRadius: 190, yRadius: 190)
    NSGraphicsContext.saveGraphicsState()
    let shadow = NSShadow()
    shadow.shadowColor = NSColor.black.withAlphaComponent(0.20)
    shadow.shadowBlurRadius = 20
    shadow.shadowOffset = NSSize(width: 0, height: -10)
    shadow.set()
    color(0x0092ff).setFill()
    shape.fill()
    NSGraphicsContext.restoreGraphicsState()
    shape.addClip()
    drawImage(NSImage(contentsOf: root.appendingPathComponent("src/assets/icon.png"))!, tile)
    NSGraphicsContext.restoreGraphicsState()
    assert(bitmap.colorAt(x: 0, y: 0)!.alphaComponent == 0)
    assert(bitmap.colorAt(x: 512, y: 512)!.alphaComponent == 1)
    try bitmap.representation(using: .png, properties: [:])!.write(to: root.appendingPathComponent("src/assets/icon-mac.png"))
    print("src/assets/icon-mac.png")
}
func render(scale: Int, preview: Bool = false) throws {
    let bitmap = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: width * scale,
        pixelsHigh: height * scale, bitsPerSample: 8, samplesPerPixel: 4,
        hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
    let context = NSGraphicsContext(bitmapImageRep: bitmap)!.cgContext
    context.translateBy(x: 0, y: CGFloat(height * scale))
    context.scaleBy(x: CGFloat(scale), y: -CGFloat(scale))
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(cgContext: context, flipped: true)
    color(0xffffff).setFill()
    NSRect(x: 0, y: 0, width: width, height: height).fill()
    // A quiet blue wash anchors the layout without competing with Finder icons.
    let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(),
        colors: [color(0xffffff).cgColor, color(0xf0f8ff).cgColor] as CFArray,
        locations: [0, 1])!
    context.drawLinearGradient(gradient, start: CGPoint(x: 0, y: 120),
        end: CGPoint(x: 0, y: height), options: [])
    drawImage(NSImage(contentsOf: root.appendingPathComponent("src/assets/logo.png"))!,
              NSRect(x: 36, y: 28, width: 32, height: 32))
    text("얼마", 78, 30, 24, "Bold", 0x0092ff)
    text("인증서 가져오기", 36, 92, 30, "Bold", 0x101214)
    text("앱을 오른쪽 폴더로 옮겨 설치해 주세요", 36, 135, 16, "Regular", 0x657281)
    // Only the arrow is baked in: app and folder remain real draggable items.
    let arrow = NSBezierPath()
    arrow.move(to: NSPoint(x: 334, y: 260)); arrow.line(to: NSPoint(x: 386, y: 260))
    arrow.move(to: NSPoint(x: 374, y: 248)); arrow.line(to: NSPoint(x: 386, y: 260))
    arrow.line(to: NSPoint(x: 374, y: 272))
    arrow.lineWidth = 4; arrow.lineCapStyle = .round; arrow.lineJoinStyle = .round
    color(0x0092ff).setStroke(); arrow.stroke()
    color(0xdceaf5).setFill()
    NSRect(x: 36, y: 393, width: 648, height: 1).fill()
    text("설치 후 Applications에서 앱을 열어주세요", 360, 415, 13, "Regular", 0x657281, centered: true)
    if preview {
        drawImage(NSImage(contentsOf: root.appendingPathComponent("src/assets/icon-mac.png"))!,
                  NSRect(x: 126, y: 196, width: 128, height: 128))
        drawImage(NSWorkspace.shared.icon(forFile: "/Applications"),
                  NSRect(x: 466, y: 196, width: 128, height: 128))
        text("얼마 인증서 가져오기", 190, 337, 13, "Regular", 0x101214, centered: true)
        text("Applications", 530, 337, 13, "Regular", 0x101214, centered: true)
    }
    NSGraphicsContext.restoreGraphicsState()
    let output = preview ? "dist/dmg-preview.png" : "installer/dmg-background\(scale == 2 ? "@2x" : "").png"
    try FileManager.default.createDirectory(at: root.appendingPathComponent(output).deletingLastPathComponent(), withIntermediateDirectories: true)
    try bitmap.representation(using: .png, properties: [:])!.write(to: root.appendingPathComponent(output))
    print(output)
}
try renderMacIcon()
try render(scale: 1)
try render(scale: 2)
if CommandLine.arguments.contains("--preview") { try render(scale: 2, preview: true) }
