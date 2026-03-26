import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * GifEncoder converts a sequence of BufferedImages to an animated GIF.
 * Uses a simple GIF format encoder for maximum compatibility.
 */
public class GifEncoder {
    private static final int NETSCAPE_EXT = 0xf9;
    private static final int IMAGE_DESC = 0x2c;
    private static final int GIF_TRAILER = 0x3b;
    private static final int GIF_HDR = 0x21;
    private static final int GIF_APP = 0xff;
    
    private final int frameDelay; // in milliseconds
    private final boolean loopInfinite;
    
    /**
     * Create a GIF encoder with specified frame delay.
     * @param frameDelayMs Frame delay in milliseconds (e.g., 67 for ~15 FPS)
     * @param loopInfinite If true, GIF will loop infinitely
     */
    public GifEncoder(int frameDelayMs, boolean loopInfinite) {
        this.frameDelay = frameDelayMs;
        this.loopInfinite = loopInfinite;
    }
    
    /**
     * Encode a sequence of frames to an animated GIF.
     */
    public void encode(List<BufferedImage> frames, OutputStream out) throws IOException {
        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("No frames to encode");
        }
        
        BufferedImage firstFrame = frames.get(0);
        int width = firstFrame.getWidth();
        int height = firstFrame.getHeight();
        
        // Write GIF header
        writeGifHeader(out, width, height);
        
        // Write application extension for looping
        if (loopInfinite) {
            writeNetscapeExtension(out);
        }
        
        // Write each frame
        int frameIndex = 0;
        for (BufferedImage frame : frames) {
            writeFrame(out, frame, width, height);
            frameIndex++;
            if (frameIndex % 10 == 0) {
                System.out.println("Encoded frame " + frameIndex + "/" + frames.size());
            }
        }
        
        // Write GIF trailer
        out.write(GIF_TRAILER);
        out.flush();
        
        System.out.println("GIF encoding complete: " + frames.size() + " frames");
    }
    
    /**
     * Write GIF 89a header
     */
    private void writeGifHeader(OutputStream out, int width, int height) throws IOException {
        out.write("GIF89a".getBytes("ASCII"));
        
        // Logical screen descriptor
        writeShort(out, width);
        writeShort(out, height);
        
        // Packed field: Global Color Table Flag=1, Color Resolution=7 (256 colors), Sort=0
        // Size of Global Color Table = 256
        out.write(0xF7); // 11110111 - global color table, 256 colors
        out.write(0);   // Background color index
        out.write(0);   // Pixel aspect ratio
        
        // Write 256-color global color table (grayscale for simplicity)
        for (int i = 0; i < 256; i++) {
            out.write(i);     // Red
            out.write(i);     // Green
            out.write(i);     // Blue
        }
    }
    
    /**
     * Write Netscape extension for infinite loop
     */
    private void writeNetscapeExtension(OutputStream out) throws IOException {
        out.write(GIF_HDR);      // Extension introducer
        out.write(GIF_APP);      // Application extension
        out.write(11);           // Block size
        out.write("NETSCAPE2.0".getBytes("ASCII"));
        out.write(3);            // Block size
        out.write(1);            // Loop sub-block ID
        writeShort(out, 0);      // Loop count (0 = infinite)
        out.write(0);            // Block terminator
    }
    
    /**
     * Write a single frame with graphics control extension
     */
    private void writeFrame(OutputStream out, BufferedImage frame, int width, int height) throws IOException {
        // Graphics Control Extension for frame delay
        out.write(GIF_HDR);      // Extension introducer
        out.write(0xF9);         // Graphics Control Label
        out.write(4);            // Block size
        out.write(0);            // Packed field (no transparency)
        writeShort(out, frameDelay / 10); // Delay time in 1/100th seconds
        out.write(0);            // Transparent color index
        out.write(0);            // Block terminator
        
        // Image descriptor
        out.write(IMAGE_DESC);
        writeShort(out, 0);      // Image left
        writeShort(out, 0);      // Image top
        writeShort(out, width);
        writeShort(out, height);
        
        // Packed field: Local Color Table Flag=0, Interlace=0, Sort=0, Reserved=0
        out.write(0x00);
        
        // Image data (LZW compression of indexed colors)
        writeIndexedImageData(out, frame, width, height);
    }
    
    /**
     * Write indexed image data with LZW compression
     */
    private void writeIndexedImageData(OutputStream out, BufferedImage frame, int width, int height) throws IOException {
        // LZW minimum code size
        out.write(8); // 8-bit LZW codes
        
        // Get pixel data
        byte[] pixels = new byte[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = frame.getRGB(x, y);
                // Convert RGB to grayscale index (0-255)
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int gray = (r + g + b) / 3;
                pixels[y * width + x] = (byte) gray;
            }
        }
        
        // Encode with simple LZW compression
        encodeLzw(out, pixels);
    }
    
    /**
     * Simple LZW encoding
     */
    private void encodeLzw(OutputStream out, byte[] data) throws IOException {
        int clearCode = 256;
        int eofCode = 257;
        int dictSize = 258;
        int codeSize = 9;
        
        // Write codes using bit packing
        BitWriter writer = new BitWriter();
        
        writer.writeBits(clearCode, codeSize);
        
        int i = 0;
        while (i < data.length) {
            writer.writeBits(data[i] & 0xFF, codeSize);
            
            // Increase code size when necessary
            if (dictSize == (1 << codeSize) && codeSize < 12) {
                codeSize++;
            }
            i++;
            
            // Periodically increase dictionary size
            dictSize++;
            if (dictSize > 4096) {
                dictSize = 258;
                codeSize = 9;
                writer.writeBits(clearCode, codeSize);
            }
        }
        
        writer.writeBits(eofCode, codeSize);
        
        // Write data blocks (max 255 bytes per block)
        byte[] encoded = writer.toByteArray();
        int pos = 0;
        while (pos < encoded.length) {
            int blockSize = Math.min(255, encoded.length - pos);
            out.write(blockSize);
            out.write(encoded, pos, blockSize);
            pos += blockSize;
        }
        out.write(0); // Block terminator
    }
    
    /**
     * Write a 16-bit little-endian short
     */
    private void writeShort(OutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }
    
    /**
     * Helper class for bit packing in LZW encoding
     */
    private static class BitWriter {
        private java.util.List<Byte> bytes = new java.util.ArrayList<>();
        private int currentByte = 0;
        private int bitPosition = 0;
        
        public void writeBits(int value, int numBits) {
            for (int i = 0; i < numBits; i++) {
                if (((value >> i) & 1) == 1) {
                    currentByte |= (1 << bitPosition);
                }
                bitPosition++;
                
                if (bitPosition == 8) {
                    bytes.add((byte) currentByte);
                    currentByte = 0;
                    bitPosition = 0;
                }
            }
        }
        
        public byte[] toByteArray() {
            if (bitPosition > 0) {
                bytes.add((byte) currentByte);
            }
            byte[] result = new byte[bytes.size()];
            for (int i = 0; i < bytes.size(); i++) {
                result[i] = bytes.get(i);
            }
            return result;
        }
    }
}
