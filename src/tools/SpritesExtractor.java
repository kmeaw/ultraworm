package tools;

import com.shavenpuppy.jglib.Resources;
import com.shavenpuppy.jglib.resources.ResourceConverter;
import com.shavenpuppy.jglib.sprites.ImageBank;
import com.shavenpuppy.jglib.sprites.SpriteImage;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

/**
 * Extracts .png files out of a .jgimage, based on the "imagebank" nodes in sprites.xml
 * Should work with most imagebanks. Might perhaps not work 100% correctly for imagebanks <= 3..
 * @author joris
 */
public class SpritesExtractor {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: SpritesExtractor <path to .xml files directory> <path to .jgimage files directory> <destination directory>");
        } else {
            File dirXMLs = new File(args[0]);
            if (!dirXMLs.isDirectory()) {
                System.out.println("Error: " + args[0] + " is not a directory.");
                System.exit(1);
            }
            File dirJGIs = new File(args[1]);
            if (!dirJGIs.isDirectory()) {
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



            try {
                ResourceConverter rc = new ResourceConverter();
                includeXML(rc, dirXMLs.getAbsolutePath() + "\\tags.xml");
                includeXML(rc, dirXMLs.getAbsolutePath() + "\\sprites.xml");

                for (int j = 0; j < 256; j++) {
                    try {
                        ImageBank bank = (ImageBank) Resources.peek("sprites" + j + ".imagebank");
                        if (bank != null) {
                            String fpath = dirJGIs.getAbsolutePath() + "\\sprites" + j + ".jgimage";
                            System.out.println("Loading: " + fpath);
                            BufferedImage bim = JGImageUtil.load(new File(fpath));
                            for (int i = 0; i < bank.numImages(); i++) {
                                SpriteImage si = bank.getImage(i);
                                BufferedImage sub = bim.getSubimage(si.getX(), si.getY(), si.getWidth(), si.getHeight());
                                ImageIO.write(sub, "png", new File(dirout.getAbsolutePath() + "\\" +  si.getName() + ".png"));
                            }
                            System.out.println(" Succesfully saved " + bank.numImages() + " files");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
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
