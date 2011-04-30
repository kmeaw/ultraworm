package tools;

import com.shavenpuppy.jglib.Image;
import com.shavenpuppy.jglib.Resources;
import com.shavenpuppy.jglib.resources.ResourceConverter;
import com.shavenpuppy.jglib.sprites.ImageBank;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

/**
 * Builds .jgimage files as specified in the sprites.xml file's "imagebank" nodes.
 * Uses an input directory where all PNGs are stored according to their internal Resource name ("spriteimage.about.puppy.01.png").
 * 
 * Tested and confirmed working (at least for sprites4.jgimage) on Retail (Steam version) v1.80.11
 * NOTE: This does not work for imagebanks below #4 due to ArrayIndexOutOfBounds exceptions.. hmm..
 * @author joris
 */
public class SpritesBuilder {

    public static final int LOWEST_WORKING_IMAGEBANK = 4;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException, Exception {
        if (args.length < 3) {
            System.out.println("Usage: SpritesBuilder <path to .xml files directory> <path to .png files directory> <destination directory>");
        } else {
            File dirXMLs = new File(args[0]);
            if (!dirXMLs.isDirectory()) {
                System.out.println("Error: " + args[0] + " is not a directory.");
                System.exit(1);
            }
            File dirPNGs = new File(args[1]);
            if (!dirPNGs.isDirectory()) {
                System.out.println("Error: " + args[1] + " is not a directory.");
                System.exit(1);
            }
            File dirout = new File(args[2]);
            if (dirout.exists() && dirout.isFile() && !dirout.isDirectory()) {
                System.out.println("Error: " + args[2] + " is a file");
                System.exit(1);
            }
            if (!dirout.exists()) {
                System.out.println("Warning: " + args[2] + " is not a directory. Creating it now.");
                dirout.mkdirs();
            }

            ResourceConverter rc = new ResourceConverter();
            includeXML(rc, dirXMLs.getAbsolutePath() + "\\tags.xml");
            includeXML(rc, dirXMLs.getAbsolutePath() + "\\sprites.xml");

            for (int j = LOWEST_WORKING_IMAGEBANK; j < 256; j++) {
                ImageBank bank = (ImageBank) Resources.peek("sprites" + j + ".imagebank");
                if (bank != null) {
                    int width = 0;
                    int height = 0;
                    int imageIOType = BufferedImage.TYPE_4BYTE_ABGR;

                    for (int i = 0; i < bank.numImages(); i++) {
                        width = Math.max(width, bank.getImage(i).getWidth() + bank.getImage(i).getX());
                        height = Math.max(height, bank.getImage(i).getHeight() + bank.getImage(i).getY());
                    }
                    BufferedImage result = new BufferedImage(width, height, imageIOType);
                    for (int i = 0; i < bank.numImages(); i++) {
                        BufferedImage bi = ImageIO.read(new File(dirPNGs.getAbsolutePath() + "\\" + bank.getImage(i).getName() + ".png"));
                        result.getRaster().setRect(bank.getImage(i).getX(), bank.getImage(i).getY(), bi.getRaster());
                    }

                    Image.write(JGImageUtil.convert_bufferedimage_to_jgimage(result), new FileOutputStream(dirout.getAbsolutePath() + "\\sprites" + j + ".jgimage"));
                }
            }
        }
    }

    private static void includeXML(ResourceConverter rc, String xmlFile) {
        System.out.println("LOADING: " + xmlFile);
        try {
            rc.include(new FileInputStream(xmlFile));
        } catch (Exception ex) {
            Logger.getLogger(SpritesExtractor.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
