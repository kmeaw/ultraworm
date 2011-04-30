/*
 * Copyright (c) 2002 Shaven Puppy Ltd
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *
 * * Neither the name of 'Shaven Puppy' nor the names of its contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.shavenpuppy.jglib.tools;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;

import javax.imageio.ImageIO;

import org.lwjgl.util.Rectangle;

import com.shavenpuppy.jglib.Image;
import com.shavenpuppy.jglib.util.ImprovedStringTokenizer;
/**
 * The texturepacker takes a selection of .PNG images and tries to cram
 * them all into a single .PNG image with dimensions which are a power of 2
 * (ie. an OpenGL texture). All "whitespace" pixels are stripped off the
 * source .PNGs to reduce their size to the minimum.
 *
 * Usage:
 * TexturePacker <sourcedir> <destdir> <nameprefix>
 *
 * This will recursively find all .PNGs in <sourcedir> and generate a number
 * of .PNG and .XML files in <destdir>. PNGs will be names <nameprefix>n.PNG,
 * XMLs will be named <nameprefix>n.XML. In each XML file there will be an
 * imagebank named <nameprefix>n.imagebank.
 */
public class TexturePacker {

	/** Default maximum texture size */
	private static final int DEFAULT_MAX_SIZE = 256;

	/** Maximum texture size */
	private int maxSize = DEFAULT_MAX_SIZE;

	/** The source directory */
	private String sourceDir;

	/** Image filter to apply */
	private ImageFilterDelegator imageFilter;

	/** The destination directory */
	private String destDir;

	/** The name prefix */
	private String namePrefix;

	/** The output XML file */
	private String outputFile;

	/** The hotspot file */
	private String hotspotFile;

	/** The style file */
	private String styleFile;

	/** The directory excludes file */
	private String excludesFile;

	/** The default styles */
	private final String defaultStyleRGBA = "transparent.style";
	private final String defaultStyleRGB = "opaque.style";

	/** A mapping of filenames to Points which are hotspots */
	private HashMap hotspotMap = new HashMap();

	/** A mapping of filenames to styles */
	private HashMap styleMap = new HashMap();

	/** The list of directories to exclude */
	private ArrayList excludesList = new ArrayList();

	/** Use jpeg compression */
	private boolean jpeg;

	/** Use linear texture magnification */
	private boolean linear;

	/** Prefix appended to output location */
	private String classpathPrefix;

	/** A quadtree of used and unused bits of texture */
	private class Quad {
		int x, y, w, h;
		Quad[] child;
		boolean allocated;

		/**
		 * Construct a Quad
		 */
		Quad(int x, int y, int w, int h) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
		}

		/**
		 * Allocate a chunk.
		 * Picks the smallest Quad in the tree below that fits.
		 * @param width The desired width
		 * @param height The desired height
		 * @return the allocated Quad or null if there was no room
		 */
		Quad allocate(int width, int height) {
			Quad smallest = findSmallest(width, height);
			if (smallest == null) {
				return null;
			}
			return smallest.doAllocate(width, height);
		}

		/**
		 * Performs the allocation. The quad is split into up to three smaller
		 * quads; the quad in the top left is allocated.
		 */
		private Quad doAllocate(int neww, int newh) {
			assert !allocated;
			if (neww == w && newh == h) {
				// Can't make any children
				allocated = true;
				return this;
			}

			if (neww == w) {
				// Only one child, at the bottom
				child = new Quad[2];
				child[0] = new Quad(x, y, neww, newh);
				child[1] = new Quad(x, y + newh, neww, h - newh);
				child[0].allocated = true;
				return child[0];
			}

			if (newh == h) {
				// Only one child, at the right
				child = new Quad[2];
				child[0] = new Quad(x, y, neww, newh);
				child[1] = new Quad(x + neww, y, w - neww, newh);
				child[0].allocated = true;
				return child[0];
			}

			child = new Quad[3];
			child[0] = new Quad(x, y, neww, newh);
			child[0].allocated = true;
			if (w - neww >  h - newh) {
				child[1] = new Quad(x + neww, y, w - neww, h);
				child[2] = new Quad(x, y + newh, neww, h - newh);
			} else {
				child[1] = new Quad(x, y + newh, w, h - newh);
				child[2] = new Quad(x + neww, y, w - neww, newh);
			}
			return child[0];
		}

		/**
		 * Find the smallest chunk that satisfies the specified required
		 * dimensions.
		 * @param width The desired width
		 * @param height The desired height
		 * @return the smallest Quad or null if there was none
		 */
		Quad findSmallest(int width, int height) {
			// Any quad already allocated or not big enough is useless
			if (allocated || this.w < width || this.h < height) {
				return null;
			}

			if (child == null) {
				// We are the smallest quad.
				return this;
			}

			Quad smallest = null;
			for (int i = 0; i < child.length; i ++) {
				Quad ret = child[i].findSmallest(width, height);
				if (smallest == null || (ret != null && ret.w * ret.h < smallest.w * smallest.h)) {
					smallest = ret;
				}
			}
			return smallest;
		}

		void draw(Graphics2D g2d) {
			if (child != null) {
				for (int i = 0; i < child.length; i ++) {
					child[i].draw(g2d);
				}
			} else {
				g2d.setColor(allocated ? Color.red : Color.green);
				g2d.drawRect(x, y, w, h);
			}
		}
	}

	int counter = 0;

	/**
	 * Packed Sprite
	 */
	private class PackedSprite {

		// Sprite source
	//	final File file;

		// Directory, relative to root path
		final String dir;

		// Sprite image name
		final String name;

		// Size
		final int width, height;

		// Source image
		BufferedImage image;

		// Rectangle offset
		final Rectangle offset = new Rectangle();

		// The quad we end up placed in
		Quad pos;

		// Have we got alpha?
		boolean alpha;

		PackedSprite(LoadedImage loadedImg) throws Exception
		{
			this.name = loadedImg.name;
			this.dir = loadedImg.dir.replace(File.separatorChar, '/');

			assert (loadedImg.img != null);
			// Do filter processing
			BufferedImage im = imageFilter.process(loadedImg.img, offset);
			// Squeeze and chop off any dead space in the image
			this.image = squeeze(im);
			this.width = image.getWidth();
			this.height = image.getHeight();
		}

		/**
		 * Squeeze an image down to its smallest possible size. Only 32 bit RGBA
		 * images can be squeezed in this way.
		 * @param src The source image
		 * @return the squeezed image, the original source image, or null if the image is empty.
		 */
		BufferedImage squeeze(BufferedImage src) throws Exception {

			offset.setLocation(0, 0);

			// We'll use the alpha channel:
			WritableRaster alphaRaster = src.getAlphaRaster();
			if (alphaRaster == null) {
				Point hotspot = getHotspot(dir, name);
				if (hotspot != null) {
					System.out.println("Applied hotspot to "+name);
					offset.setLocation(hotspot.x, hotspot.y);
				}

				return src;
			} else {
				this.alpha = true;
			}

			// Scan across the top:
			final byte[] a = new byte[1];
			int top = 0;
			outer0: for (int y = 0; y < src.getHeight(); y ++) {
				for (int x = 0; x < src.getWidth(); x ++) {
					alphaRaster.getDataElements(x, y, a);
					if (a[0] != 0) {
						break outer0;
					}
				}
				top ++;
			}
			int bottom = src.getHeight() - 1;
			outer1: for (int y = src.getHeight(); --y >= top; ) {
				for (int x = 0; x < src.getWidth(); x ++) {
					alphaRaster.getDataElements(x, y, a);
					if (a[0] != 0) {
						break outer1;
					}
				}
				bottom --;
			}

			int left = 0;
			outer2: for (int x = 0; x < src.getWidth(); x ++) {
				for (int y = top; y <= bottom; y ++) {
					alphaRaster.getDataElements(x, y, a);
					if (a[0] != 0) {
						break outer2;
					}
				}
				left ++;
			}
			int right = src.getWidth() - 1;
			outer3: for (int x = src.getWidth(); --x >= left; ) {
				for (int y = top; y <= bottom; y ++) {
					alphaRaster.getDataElements(x, y, a);
					if (a[0] != 0) {
						break outer3;
					}
				}
				right --;
			}


			int newWidth = 1 + right - left;
			int newHeight = 1 + bottom - top;
			if (newWidth <= 0 || newHeight <= 0) {
				throw new Exception("Image is completely alpha'd away!");
			}
			BufferedImage dest = src.getSubimage(left, top, newWidth, newHeight);
			// Use defined hotspot if there is one; otherwise use the centre of the image
			Point hotspot = getHotspot(dir, name);
			if (hotspot != null) {
				System.out.println("Applied hotspot to "+name);
				int yDec = src.getHeight() - 2 - bottom;
				offset.setLocation(hotspot.x - left, hotspot.y - yDec - 1);
			} else {
				offset.setLocation(src.getWidth() / 2 - left, bottom - src.getHeight() / 2);
			}
			offset.setSize(1 + right - left, 1 + bottom - top);
			return dest;
		}

	}

	private Point getHotspot(String dir, String name) {
		Point hotspot;
		if (name != null) {
			hotspot = (Point) hotspotMap.get(name);
			if (hotspot != null) {
				return hotspot;
			}
		}

		return (Point) hotspotMap.get(dir);
//		hotspot = (Point) hotspotMap.get(dir);
//		if (hotspot != null) {
//			return hotspot;
//		}
//		// Strip path
//		int idx = dir.indexOf('/');
//		if (idx != -1) {
//			dir = dir.substring(0, idx);
//			System.out.println("Check directory "+dir);
//			return getHotspot(dir, null);
//		}
//
//		return null;
	}

	/**
	 * A PackedTexture
	 */
	private class PackedTexture {

		// Marks the used areas
		final Quad area = new Quad(0, 0, maxSize, maxSize);

		// The image we're writing to
		BufferedImage image;

		// List of stuff stashed so far
		final ArrayList packedSprites = new ArrayList();

		int count;

		/** Pixels used so far */
		private int used;

		/** Type: RGB or RGBA */
		private final String type;

		PackedTexture(String type) {
			this.type = type;
			if (type.equals("RGB")) {
				ColorModel cm = new DirectColorModel(24, 0xFF, 0xFF00, 0xFF0000);//.;ColorSpace.getInstance(ColorSpace.CS_sRGB), true, false, ColorModel.OPAQUE, DataBuffer.TYPE_BYTE);
				WritableRaster wr = cm.createCompatibleWritableRaster(maxSize, maxSize);
				image =  new BufferedImage(cm, wr, false, null);
			} else {
				ColorModel cm = new DirectColorModel(32, 0xFF, 0xFF00, 0xFF0000, 0xFF000000);//new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB), true, false, ColorModel.TRANSLUCENT, DataBuffer.TYPE_BYTE);
				WritableRaster wr = cm.createCompatibleWritableRaster(maxSize, maxSize);
				image =  new BufferedImage(cm, wr, false, null);
			}

			count = counter ++;
		}

		/**
		 * Attempt to pack an image into this packedtexture. If successful
		 * return true, otherwise return false.
		 * @return true if the image fit
		 */
		boolean pack(PackedSprite sprite) {

			// If the image is gone, we're no longer accepting any more sprites...
			if (image == null) {
				return false;
			}

			int w = sprite.image.getWidth();
			int h = sprite.image.getHeight();
			if (w < maxSize) {
				w ++;
			}

			if (h < maxSize) {
				h ++;
			}

			sprite.pos = area.allocate(w, h);
			if (sprite.pos == null) {
				return false;
			}

			Graphics2D g2d = (Graphics2D) image.getGraphics();
			g2d.drawImage(sprite.image, sprite.pos.x, sprite.pos.y, null);
			packedSprites.add(sprite);

			used += w * h;
			return true;
		}

		/**
		 * Have we got an image?
		 * @return true if we're still accepting stuff
		 */
		boolean hasImage() {
			return image != null;
		}

		/**
		 * @return true if we're > 95% full
		 */
		boolean isFull() {
			return image == null || (used > (int)(image.getWidth() * image.getHeight() * 0.95));
		}

		void writeImage() throws Exception {
			String name = namePrefix+count;
			System.out.println("Writing "+name+".jgimage : space wasted "+(100.0f - (100.0f * used / (image.getWidth() * image.getHeight()) ))+"%");

			Image convertedImage = ImageConverter.convert(image, jpeg);
			Image.write(convertedImage, new BufferedOutputStream(new FileOutputStream(new File(destDir+File.separatorChar+name+".jgimage"))));

//			JFrame f = new JFrame();
//			f.getContentPane().add(new JLabel(new ImageIcon(image)));
//			f.setSize(image.getWidth(), image.getHeight());
//			f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
//			f.setVisible(true);
			// Save some RAM
			image.flush();
			image = null;
			for (Iterator i = packedSprites.iterator(); i.hasNext(); ) {
				PackedSprite ps = ((PackedSprite) i.next());
				ps.image.flush();
				ps.image = null;
			}
		}

		void writeXML(BufferedWriter bw) throws Exception {
			if (hasImage()) {
				writeImage();
			}

			String name = namePrefix+count;

			// And the XML snippet
			// Write out textureimage:
			// Write out texture definition:
			bw.write("\t<texture name=\"");
			bw.write(name);
			bw.write(".texture\" url=\"classpath:");
			bw.write(classpathPrefix);
			bw.write(name);
			bw.write(".jgimage\" dst=\"GL_");
			bw.write(type);
			bw.write("\" target=\"GL_TEXTURE_2D\" min=\"GL_LINEAR\" mag=\"");
			if (linear) {
				bw.write("GL_LINEAR");
			} else {
				bw.write("GL_NEAREST");
			}
			bw.write("\" wrap=\"GL_FALSE\" />\n");

			// Write out sprite image bank:
			bw.write("\t<imagebank name=\"");
			bw.write(name);
			bw.write(".imagebank\" texture=\"");
			bw.write(name);
			if (type.equals("RGB")) {
				bw.write(".texture\" defaultstyle=\""+defaultStyleRGB+"\">\n");
			} else {
				bw.write(".texture\" defaultstyle=\""+defaultStyleRGBA+"\">\n");
			}

			for (Iterator i = packedSprites.iterator(); i.hasNext(); ) {
				PackedSprite sprite = (PackedSprite) i.next();

				bw.write("\t\t<spriteimage name=\"spriteimage.");
				bw.write(sprite.name);
				bw.write("\" x=\"");
				bw.write(String.valueOf(sprite.pos.x));
				bw.write("\" y=\"");
				bw.write(String.valueOf(sprite.pos.y));
				bw.write("\" w=\"");
				bw.write(String.valueOf(sprite.width));
				bw.write("\" h=\"");
				bw.write(String.valueOf(sprite.height));
				bw.write("\" hx=\"");
				bw.write(String.valueOf(sprite.offset.getX()));
				bw.write("\" hy=\"");
				bw.write(String.valueOf(sprite.offset.getY()));
				bw.write("\" ");

				// Write style if it's available
				String style = (String) styleMap.get(sprite.name);
				if (style == null) {
					style = (String) styleMap.get(sprite.dir);
				}
				if (style != null) {
					bw.write("style=\"");
					bw.write(style);
					bw.write("\" ");
					System.out.println("Style for "+sprite.dir+":"+sprite.name+" is "+style);
				}
				bw.write("/>\n");
			}

			bw.write("\t</imagebank>\n");
		}
	}

	/**
	 * Constructor for TexturePacker.
	 */
	public TexturePacker(String sourceDir, ImageFilterDelegator filterDel, String destDir, String namePrefix, String outputFile, String hotspotFile, String styleFile, String excludesFile, String maxSize, boolean jpeg, boolean linear, String classpathPrefix)
	{
		this.sourceDir = sourceDir;
		this.imageFilter = filterDel;
		this.destDir = destDir;
		this.namePrefix = namePrefix;
		this.outputFile = outputFile;
		this.hotspotFile = hotspotFile;
		this.styleFile = styleFile;
		this.excludesFile = excludesFile;
		this.maxSize = Integer.parseInt(maxSize);
		this.jpeg = jpeg;
		this.linear = linear;
		this.classpathPrefix = classpathPrefix;
	}

	/**
	 * Recursively find a list of source .PNGs
	 * @param dir The directory to recurse
	 * @param ret An arraylist to store the resulting files in
	 */
	private void getSourcePNGs(File dir, ArrayList ret) throws Exception {
		// Get the list of source .PNGs
		System.out.println("Scanning "+dir);
		if (!dir.exists()) {
			throw new Exception("Source directory '"+dir+"' does not exist.");
		}
		if (!dir.isDirectory()) {
			throw new Exception("Source '"+sourceDir+"' is not a directory.");
		}
		if (excludesList.contains(dir.getName())) {
			return;
		}
		File[] file = dir.listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				String name = pathname.getName();

				if  (pathname.isDirectory()) {
					return !pathname.equals("CVS");
				} else if
					(
						name.endsWith(".png")
					|| 	name.endsWith(".tga")
					)
				{
					return imageFilter.getFileFilter().accept(pathname);
				} else {
					return false;
				}
			}
		});
		for (int i = 0; i < file.length; i ++) {
			if (file[i].isFile()) {
				System.out.println("Adding "+file[i]);
				ret.add(file[i]);
			} else {
				getSourcePNGs(file[i], ret);
			}
		}
	}

	/**
	 * Reads in the hotspot control file, which is a list of filenames followed by coordinates
	 */
	private void readHotspots() throws Exception {
		BufferedReader br = new BufferedReader(new FileReader(hotspotFile));

		String line;
		while ( (line = br.readLine()) != null) {
			line.trim();
			// Ignore comments
			if (line.startsWith("//") || line.startsWith("\'") || line.startsWith("#") || line.startsWith(";") || line.equals("")) {
				continue;
			}

			ImprovedStringTokenizer st = new ImprovedStringTokenizer(line);

			String fileName = st.nextToken();
			Point hotspot = new Point(Integer.parseInt(st.nextToken()), Integer.parseInt(st.nextToken()));
			hotspotMap.put(fileName, hotspot);

		}

		br.close();
	}

	/**
	 * Reads in the rendering style control file, which is a list of filenames followed by rendering styles
	 */
	private void readStyles() throws Exception {
		BufferedReader br = new BufferedReader(new FileReader(styleFile));

		String line;
		while ( (line = br.readLine()) != null) {
			line.trim();
			// Ignore comments
			if (line.startsWith("//") || line.startsWith("\'") || line.startsWith("#") || line.startsWith(";") || line.equals("")) {
				continue;
			}

			ImprovedStringTokenizer st = new ImprovedStringTokenizer(line);

			String fileName = st.nextToken();
			String style = st.nextToken();
			styleMap.put(fileName, style);

		}

		br.close();
	}

	/**
	 * Reads in the excludes control file, which is a list of directory names to exclude
	 */
	private void readExcludes() throws Exception {
		BufferedReader br = new BufferedReader(new FileReader(excludesFile));

		String line;
		while ( (line = br.readLine()) != null) {
			line.trim();
			// Ignore comments
			if (line.startsWith("//") || line.startsWith("\'") || line.startsWith("#") || line.startsWith(";") || line.equals("")) {
				continue;
			}
			excludesList.add(line);
		}

		br.close();
	}



	/**
	 * Perform the packing operation
	 */
	private void pack() throws Exception {

		Image.setCompressor(new JPEGCompressor());

		// Read the hotspot control file
		readHotspots();

		// Read style control file
		readStyles();

		// Read exclusions
		readExcludes();

		// Get the list of source .PNGs
		File sourceDirF = new File(sourceDir);
		ArrayList sourcePNGs = new ArrayList(128);
		getSourcePNGs(sourceDirF, sourcePNGs);

		// Load into a list of LoadedImage objects
		ArrayList loadedImgs = loadImageFiles(sourcePNGs);

		// Load each one
		ArrayList packedSpritesRGB = new ArrayList(loadedImgs.size());
		ArrayList packedSpritesRGBA = new ArrayList(loadedImgs.size());

		for (Iterator i = loadedImgs.iterator(); i.hasNext(); )
		{
			LoadedImage img = (LoadedImage)i.next();
			try {
				PackedSprite ps = new PackedSprite(img);
				if (ps.alpha) {
					packedSpritesRGBA.add(ps);
				} else {
					packedSpritesRGB.add(ps);
				}
			} catch (Exception e) {
				System.out.println("FAILED to pack "+img.name+": "+e);
			}
		}

		// Here's the array of PackedTextures
		ArrayList packedTexturesRGB = new ArrayList();
		ArrayList packedTexturesRGBA = new ArrayList();

//		stash("RGB4", packedSpritesRGB, packedTexturesRGB);
//		stash("RGBA4", packedSpritesRGBA, packedTexturesRGBA);
		stash("RGB", packedSpritesRGB, packedTexturesRGB);
		stash("RGBA", packedSpritesRGBA, packedTexturesRGBA);

		// Output the XML snippet
		File outputXMLf = new File(outputFile);
		outputXMLf.getAbsoluteFile().getParentFile().mkdirs();

		System.out.println("Writing to "+outputFile);
		BufferedWriter bw = new BufferedWriter(new FileWriter(outputXMLf));

		bw.write("<?xml version='1.0' encoding='utf-8'?>\n<resources>\n");

		for (Iterator i = packedTexturesRGB.iterator(); i.hasNext(); ) {
			PackedTexture pt = (PackedTexture) i.next();
			pt.writeXML(bw);
		}
		for (Iterator i = packedTexturesRGBA.iterator(); i.hasNext(); ) {
			PackedTexture pt = (PackedTexture) i.next();
			pt.writeXML(bw);
		}

		bw.write("</resources>\n");
		bw.flush();
		bw.close();
	}

	private void stash(String type, ArrayList packedSprites, ArrayList packedTextures) throws Exception {
		// For each image, attempt to pack it into a PackedTexture
		for (int i = 0; i < packedSprites.size(); i ++) {
			boolean stashed = false;

			PackedSprite packedSprite = (PackedSprite) packedSprites.get(i);

			/*
			for (Iterator store = packedTextures.iterator(); store.hasNext(); ) {
				PackedTexture pt = (PackedTexture) store.next();
				stashed = pt.pack(packedSprite);
				if (stashed)
					break;
			}
			*/
			if (packedTextures.size() > 0) {
				PackedTexture pt = (PackedTexture) packedTextures.get(packedTextures.size() - 1);
				stashed = pt.pack(packedSprite);
				if (!stashed && pt.hasImage()) {
					pt.writeImage();
				}
			}
			if (!stashed) {
				PackedTexture pt2 = new PackedTexture(type);
				stashed = pt2.pack(packedSprite);
				if (!stashed) {
					throw new Exception("Image "+packedSprite.name+" is too big");
				}
				packedTextures.add(pt2);
			}
		}
	}

	/** Accepts an ArrayList of File objects which are image files to load.
	 *  Returns an ArrayList of LoadedImage objects for packing.
	 */
	private ArrayList loadImageFiles(ArrayList filesList)
	{
		ArrayList loadedImages = new ArrayList();



		// Loop over all input files
		for (int i=0; i<filesList.size(); i++)
		{
			try
			{
				File file = (File)filesList.get(i);

//				System.out.println("File:"+file);

				// Get base name and image
				String baseName = file.getName().substring(0, file.getName().length() - 4);
				BufferedImage baseImage = ImageIO.read(file);
				String dir = "";

				File parentDir = file.getParentFile();
				if (parentDir != null)
				{
					if ( !parentDir.equals( new File(sourceDir) ) )
					{
						int offset = sourceDir.length() + 1;
						dir = parentDir.getPath().substring(offset);
					}
				}


				loadedImages.add( new LoadedImage(baseName, dir, baseImage));
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}

		return imageFilter.preProcess(loadedImages);
	}

	/**
	 * Start the application
	 * Usage:
	 * TexturePacker <sourcedir> [shadows|sprites] <destdir> <nameprefix> <outputxmlfile> <hotspotfile> <stylefile> <excludesfile>
	 */
	public static void main(String[] args) {

		if (args.length < 11) {
			System.err.println("Usage:");
			System.err.println("\tTexturePacker <sourcedir> <imagefilter> <destdir> <nameprefix> <outputxmlfile> <hotspotfile> <stylefile> <excludesfile> <maxsize> [jpeg | *] [linear | nearest] [ classpathDir ]");
			System.exit(-1);
		}

		ImageIO.setUseCache(false);

		try
		{
			ImageFilterDelegator filterDel = new ImageFilterDelegator(args[1]);


			boolean isJpeg = args[9].equals("jpeg");
			boolean isLinear = args[10].equals("linear");

			String classpathPrefix;
			if (args.length >= 12) {
				classpathPrefix = args[11];
			} else {
				classpathPrefix = "";
			}

			new TexturePacker(args[0], filterDel, args[2], args[3], args[4], args[5], args[6], args[7], args[8], isJpeg, isLinear, classpathPrefix).pack();
		}
		catch (Exception e)
		{
			e.printStackTrace(System.err);
			System.exit(-1);
		}
	}

}
