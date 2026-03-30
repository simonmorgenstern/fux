import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * GifEncoder converts a sequence of BufferedImages to an animated GIF
 * using Java's built-in ImageIO GIF writer.
 */
public class GifEncoder {
    private final int frameDelay; // in milliseconds
    private final boolean loopInfinite;

    public GifEncoder(int frameDelayMs, boolean loopInfinite) {
        this.frameDelay = frameDelayMs;
        this.loopInfinite = loopInfinite;
    }

    public void encode(List<BufferedImage> frames, OutputStream out) throws IOException {
        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("No frames to encode");
        }

        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        MemoryCacheImageOutputStream imageOut = new MemoryCacheImageOutputStream(out);
        writer.setOutput(imageOut);

        ImageWriteParam params = writer.getDefaultWriteParam();
        ImageTypeSpecifier imageType = ImageTypeSpecifier.createFromBufferedImageType(
            BufferedImage.TYPE_INT_RGB);

        IIOMetadata metadata = writer.getDefaultImageMetadata(imageType, params);

        // Configure frame delay and disposal
        String metaFormat = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metaFormat);

        // Graphics control extension — frame delay
        IIOMetadataNode gce = getOrCreateNode(root, "GraphicControlExtension");
        gce.setAttribute("disposalMethod", "restoreToBackgroundColor");
        gce.setAttribute("userInputFlag", "FALSE");
        gce.setAttribute("transparentColorFlag", "FALSE");
        gce.setAttribute("delayTime", String.valueOf(frameDelay / 10)); // in 1/100s
        gce.setAttribute("transparentColorIndex", "0");

        // Application extension — loop count
        if (loopInfinite) {
            IIOMetadataNode appExts = getOrCreateNode(root, "ApplicationExtensions");
            IIOMetadataNode appExt = new IIOMetadataNode("ApplicationExtension");
            appExt.setAttribute("applicationID", "NETSCAPE");
            appExt.setAttribute("authenticationCode", "2.0");
            appExt.setUserObject(new byte[]{0x01, 0x00, 0x00}); // loop forever
            appExts.appendChild(appExt);
        }

        metadata.setFromTree(metaFormat, root);

        writer.prepareWriteSequence(null);

        for (int i = 0; i < frames.size(); i++) {
            BufferedImage frame = frames.get(i);

            // Convert to TYPE_INT_ARGB if needed for proper color handling
            BufferedImage converted = new BufferedImage(
                frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_ARGB);
            converted.getGraphics().drawImage(frame, 0, 0, null);

            // For subsequent frames, reuse same metadata (same delay)
            IIOMetadata frameMeta = writer.getDefaultImageMetadata(
                ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_ARGB), params);
            IIOMetadataNode frameRoot = (IIOMetadataNode) frameMeta.getAsTree(metaFormat);
            IIOMetadataNode frameGce = getOrCreateNode(frameRoot, "GraphicControlExtension");
            frameGce.setAttribute("disposalMethod", "restoreToBackgroundColor");
            frameGce.setAttribute("userInputFlag", "FALSE");
            frameGce.setAttribute("transparentColorFlag", "FALSE");
            frameGce.setAttribute("delayTime", String.valueOf(frameDelay / 10));
            frameGce.setAttribute("transparentColorIndex", "0");
            frameMeta.setFromTree(metaFormat, frameRoot);

            writer.writeToSequence(new IIOImage(converted, null, frameMeta), params);

            if ((i + 1) % 10 == 0) {
                System.out.println("Encoded frame " + (i + 1) + "/" + frames.size());
            }
        }

        writer.endWriteSequence();
        imageOut.close();
        writer.dispose();

        System.out.println("GIF encoding complete: " + frames.size() + " frames");
    }

    private static IIOMetadataNode getOrCreateNode(IIOMetadataNode root, String nodeName) {
        for (int i = 0; i < root.getLength(); i++) {
            if (root.item(i).getNodeName().equalsIgnoreCase(nodeName)) {
                return (IIOMetadataNode) root.item(i);
            }
        }
        IIOMetadataNode node = new IIOMetadataNode(nodeName);
        root.appendChild(node);
        return node;
    }
}
