package tools;

import com.shavenpuppy.jglib.Image;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;

/**
 * Class to load (some?) .jgimage files and/or convert them to PNGs.
 * @author joris
 */
public class JGImageUtil {

    public static void convert_jgimage_to_png(File infile, File outfile) {
        try {
            BufferedImage bim = load(infile);
            ImageIO.write(bim, "png", outfile);

        } catch (Exception e) {
            System.err.println("ERROR CONVERTING " + infile.getAbsolutePath());
            e.printStackTrace();
        }
    }
    
    public static Image convert_bufferedimage_to_jgimage(BufferedImage input) {
            DataBuffer db = input.getRaster().getDataBuffer();
            DataBufferByte dbb = (DataBufferByte) db;
            Image img = new Image(input.getWidth(), input.getHeight(), Image.ABGR, dbb.getData());
            return img;
    }

    public static BufferedImage load(File infile) throws FileNotFoundException, Exception {
        Image i = Image.read(new FileInputStream(infile));
        ByteBuffer bb = i.getData();
        ByteBuffer buffer = ByteBuffer.allocate(bb.capacity());


        buffer.position(0);
        buffer.put(bb);

        BufferedImage bim = null;
        if (i.getType() == 1) {
            bim = convertType1(i, buffer);
        }

        if (i.getType() == 0) {
            bim = convertType0(i, buffer);
        }

        if (i.getType() == 4) {
            bim = convertType4(i, buffer);
        }

        if (i.getType() == 5) {
            bim = convertType5(i, buffer);
        }


        if (bim == null) {
            throw new Exception("Error! No converter made for type " + i.getType());
        }
        return bim;
    }

    private static BufferedImage convertType0(Image i, ByteBuffer buffer) {
        ColorModel colorModel =
                new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB),
                new int[]{8, 8, 8},
                false,
                false,
                Transparency.TRANSLUCENT,
                DataBuffer.TYPE_BYTE);
        WritableRaster raster =
                Raster.createInterleavedRaster(new DataBufferByte(buffer.array(), buffer.capacity()),
                i.getWidth(),
                i.getHeight(),
                i.getWidth() * 3,
                3,
                new int[]{0, 1, 2},
                null);

        BufferedImage bfImage = new BufferedImage(colorModel, raster, false, null);
        return bfImage;
    }

    private static BufferedImage convertType1(Image i, ByteBuffer buffer) {
        ColorModel colorModel =
                new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB),
                new int[]{8, 8, 8, 8},
                true,
                false,
                Transparency.TRANSLUCENT,
                DataBuffer.TYPE_BYTE);
        WritableRaster raster =
                Raster.createInterleavedRaster(new DataBufferByte(buffer.array(), buffer.capacity()),
                i.getWidth(),
                i.getHeight(),
                i.getWidth() * 4,
                4,
                new int[]{0, 1, 2, 3},
                null);

        BufferedImage bfImage = new BufferedImage(colorModel, raster, false, null);
        return bfImage;
    }

    
    private static BufferedImage convertType4(Image i, ByteBuffer buffer) {
        ColorModel colorModel =
                new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB),
                new int[]{8, 8, 8, 8},
                true,
                false,
                Transparency.TRANSLUCENT,
                DataBuffer.TYPE_BYTE);
        WritableRaster raster =
                Raster.createInterleavedRaster(new DataBufferByte(buffer.array(), buffer.capacity()),
                i.getWidth(),
                i.getHeight(),
                i.getWidth() * 4,
                4,
                new int[]{1, 2, 3, 0},
                null);

        BufferedImage bfImage = new BufferedImage(colorModel, raster, false, null);
        return bfImage;
    }

     private static BufferedImage convertType5(Image i, ByteBuffer buffer) {
        ColorModel colorModel =
                new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB),
                new int[]{8, 8, 8, 8},
                true,
                false,
                Transparency.TRANSLUCENT,
                DataBuffer.TYPE_BYTE);
        WritableRaster raster =
                Raster.createInterleavedRaster(new DataBufferByte(buffer.array(), buffer.capacity()),
                i.getWidth(),
                i.getHeight(),
                i.getWidth() * 4,
                4,
                new int[]{3, 2, 1, 0},
                null);

        BufferedImage bfImage = new BufferedImage(colorModel, raster, false, null);
        return bfImage;
    }
}