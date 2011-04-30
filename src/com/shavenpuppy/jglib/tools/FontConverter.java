/*
 * Copyright (c) 2003-onwards Shaven Puppy Ltd
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.*;
import java.io.*;
import java.util.ArrayList;

import javax.swing.JFrame;
import javax.swing.WindowConstants;

import com.shavenpuppy.jglib.*;
import com.shavenpuppy.jglib.Font;
import com.shavenpuppy.jglib.Image;
import com.shavenpuppy.jglib.util.Util;
import tools.JGImageUtil;

/**
 * Converts Java fonts into jgfonts.
 * Usage:
 * FontConverter <srcfont> <destfontfilename> <maxchars>
 * Srcfont is like Arial-BOLD-12.
 * Maxchars will be 127 for ascii or 65536 for unicode.
 */
public class FontConverter {

	private static final boolean DEBUG = false;

	/** Safety border between glyphs */
	private static final int BORDER = 4;

	String srcFontName;
	String outputDir;
	java.awt.Font srcFont;
	Font destFont;
	int maxChars;
	boolean blur;


	/**
	 * Lord have mercy. A graphics2d which is implemented by OpenGL. Whatever next?
	 * This is just a hacked class to enable us to get at the glyphs being rendered by
	 * a TextLayout object.
	 */
	private class GLGraphics2D extends Graphics2D {

		// Create temp dummy image which we can get a font rendering context from
		private final BufferedImage image;
		private final Graphics2D g2d;
		private final FontRenderContext frc;
		private final FontMetrics metrics;

		private float[] xxx = new float[2];
		private float[] yyy = new float[2];

		// Used when rendering a font
		private int numGlyphsDrawn;
		private int glyphPos;

		public GLGraphics2D() {
			image = new BufferedImage(1, 1, BufferedImage.TYPE_4BYTE_ABGR);
			g2d = (Graphics2D) image.getGraphics();
			g2d.setFont(srcFont);
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			frc = g2d.getFontRenderContext();
			metrics = g2d.getFontMetrics(srcFont);
		}

		/*
		 * These are the methods which are actually called when rendering a font with TextLayout
		 */
		@Override
		public java.awt.Font getFont() {
			return srcFont;
		}
		@Override
		public FontMetrics getFontMetrics(java.awt.Font f) {
			return metrics;
		}
		@Override
		public FontRenderContext getFontRenderContext() {
			return frc;
		}
		@Override
		public void drawGlyphVector(GlyphVector g, float x, float y) {
			final int n = g.getNumGlyphs();
			for (int i = 0; i < n && i < 2; i++) {
				Point2D pos = g.getGlyphPosition(i);
				xxx[glyphPos] = (float) pos.getX() + x;
				yyy[glyphPos++] = (float) pos.getY() + y;
				numGlyphsDrawn++;
			}
		}

		public void reset() {
			glyphPos = 0;
			numGlyphsDrawn = 0;
		}
		public int getNumGlyphsDrawn() {
			return numGlyphsDrawn;
		}

		/*
		 * The following methods are just stubs to create a concrete class. During font rendering with a TextLayout,
		 * none of these methods are actually called, so we can get away with not implementing any of them properly.
		 */
		@Override
		public void addRenderingHints(java.util.Map hints) {
		}
		@Override
		public void clearRect(int x, int y, int width, int height) {
		}
		@Override
		public void clip(Shape s) {
		}
		@Override
		public void clipRect(int x, int y, int width, int height) {
		}
		@Override
		public void copyArea(int x, int y, int width, int height, int dx, int dy) {
		}
		@Override
		public Graphics create() {
			return this;
		}
		@Override
		public void dispose() {
		}
		@Override
		public void draw(Shape s) {
		}
		@Override
		public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
		}
		@Override
		public void drawImage(java.awt.image.BufferedImage img, java.awt.image.BufferedImageOp op, int x, int y) {
		}
		@Override
		public boolean drawImage(
			java.awt.Image img,
			int dx1,
			int dy1,
			int dx2,
			int dy2,
			int sx1,
			int sy1,
			int sx2,
			int sy2,
			java.awt.Color bgcolor,
			java.awt.image.ImageObserver observer) {
			return false;
		}
		@Override
		public boolean drawImage(
			java.awt.Image img,
			int dx1,
			int dy1,
			int dx2,
			int dy2,
			int sx1,
			int sy1,
			int sx2,
			int sy2,
			java.awt.image.ImageObserver observer) {
			return false;
		}
		@Override
		public boolean drawImage(
			java.awt.Image img,
			int x,
			int y,
			int width,
			int height,
			java.awt.Color bgcolor,
			java.awt.image.ImageObserver observer) {
			return false;
		}
		@Override
		public boolean drawImage(java.awt.Image img, int x, int y, int width, int height, java.awt.image.ImageObserver observer) {
			return false;
		}
		@Override
		public boolean drawImage(java.awt.Image img, int x, int y, java.awt.Color bgcolor, java.awt.image.ImageObserver observer) {
			return false;
		}
		@Override
		public boolean drawImage(java.awt.Image img, int x, int y, java.awt.image.ImageObserver observer) {
			return false;
		}
		@Override
		public boolean drawImage(java.awt.Image img, AffineTransform xform, java.awt.image.ImageObserver obs) {
			return false;
		}
		@Override
		public void drawLine(int x1, int y1, int x2, int y2) {
		}
		@Override
		public void drawOval(int x, int y, int width, int height) {
		}
		@Override
		public void drawPolygon(int[] xPoints, int[] yPoints, int nPoints) {
		}
		@Override
		public void drawPolyline(int[] xPoints, int[] yPoints, int nPoints) {
		}
		@Override
		public void drawRenderableImage(java.awt.image.renderable.RenderableImage img, AffineTransform xform) {
		}
		@Override
		public void drawRenderedImage(java.awt.image.RenderedImage img, AffineTransform xform) {
		}
		@Override
		public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
		}
		@Override
		public void drawString(String s, float x, float y) {
		}
		@Override
		public void drawString(String str, int x, int y) {
		}
		@Override
		public void drawString(java.text.AttributedCharacterIterator iterator, float x, float y) {
		}
		@Override
		public void drawString(java.text.AttributedCharacterIterator iterator, int x, int y) {
		}
		@Override
		public void fill(Shape s) {
		}
		@Override
		public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
		}
		@Override
		public void fillOval(int x, int y, int width, int height) {
		}
		@Override
		public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints) {
		}
		@Override
		public void fillRect(int x, int y, int width, int height) {
		}
		@Override
		public void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
		}
		@Override
		public java.awt.Color getBackground() {
			return null;
		}
		@Override
		public Shape getClip() {
			return null;
		}
		@Override
		public java.awt.Rectangle getClipBounds() {
			return null;
		}
		@Override
		public java.awt.Color getColor() {
			return null;
		}
		@Override
		public Composite getComposite() {
			return null;
		}
		@Override
		public GraphicsConfiguration getDeviceConfiguration() {
			return null;
		}
		@Override
		public Paint getPaint() {
			return null;
		}
		@Override
		public Object getRenderingHint(RenderingHints.Key hintKey) {
			return null;
		}
		@Override
		public RenderingHints getRenderingHints() {
			return null;
		}
		@Override
		public Stroke getStroke() {
			return null;
		}
		@Override
		public AffineTransform getTransform() {
			return null;
		}
		@Override
		public boolean hit(java.awt.Rectangle rect, Shape s, boolean onStroke) {
			return false;
		}
		@Override
		public void rotate(double theta) {
		}
		@Override
		public void rotate(double theta, double x, double y) {
		}
		@Override
		public void scale(double sx, double sy) {
		}
		@Override
		public void setBackground(java.awt.Color color) {
		}
		@Override
		public void setClip(int x, int y, int width, int height) {
		}
		@Override
		public void setClip(Shape clip) {
		}
		@Override
		public void setColor(java.awt.Color c) {
		}
		@Override
		public void setComposite(Composite comp) {
		}
		@Override
		public void setFont(java.awt.Font font) {
		}
		@Override
		public void setPaint(Paint paint) {
		}
		@Override
		public void setPaintMode() {
		}
		@Override
		public void setRenderingHint(RenderingHints.Key hintKey, Object hintValue) {
		}
		@Override
		public void setRenderingHints(java.util.Map hints) {
		}
		@Override
		public void setStroke(Stroke s) {
		}
		@Override
		public void setTransform(AffineTransform tx) {
		}
		@Override
		public void setXORMode(java.awt.Color c1) {
		}
		@Override
		public void shear(double shx, double shy) {
		}
		@Override
		public void transform(AffineTransform tx) {
		}
		@Override
		public void translate(double tx, double ty) {
		}
		@Override
		public void translate(int x, int y) {
		}
	}

	/**
	 * Constructor for FontConverter.
	 * Creates a JGLIB font from the specified Java font.
	 * The resulting font can be read with getFont().
	 */
	public FontConverter(String srcFontName, int maxChars, boolean blur) {
		this.maxChars = maxChars;
		this.srcFontName = srcFontName;
		this.blur = blur;

		System.out.println("Exporting font '"+srcFontName+"' ("+maxChars+" chars)");
	}

	/**
	 * Font converter args:
	 * <input font name> <output font file name> <maxchars> [blur]
	 * Example:
	 * FontConverter Arial-PLAIN-12 c:\Projects\Blah\arial-plain-12.glfont 65536 blur
	 * @param args
	 */

	public static void main(String[] args) {
		try {
			for (int i = 0; i < args.length; i += 3) {
				File destFile = new File(args[i + 1]);
				File parentDir = destFile.getAbsoluteFile().getParentFile();
				parentDir.mkdirs();

				boolean blur = i < args.length - 3 && args[i + 3].equals("blur");
				FontConverter fc = new FontConverter(args[i], Integer.parseInt(args[i + 2]), blur);
				if (blur) {
					i ++;
				}

				ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(destFile)));
				fc.doCreate();
                                
				oos.writeObject(fc.destFont);
				oos.flush();
				oos.close();
  
				System.out.println("Exported to file '"+destFile+"'");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	protected Font doCreate() {

		if (maxChars > Character.MAX_VALUE) {
			maxChars = Character.MAX_VALUE;
		}

		srcFont = java.awt.Font.decode(srcFontName);
		BufferedImage tempImage = new BufferedImage(1, 1, BufferedImage.TYPE_4BYTE_ABGR_PRE);
		Graphics2D gl2d = (Graphics2D) tempImage.getGraphics();
		FontRenderContext frc = gl2d.getFontRenderContext();

		// Create the glyphs for the first MAX_GLYPHS characters
		GlyphVector[] gv = new GlyphVector[maxChars];
		int[] mapping = new int[maxChars];
		boolean[] fixedWidth = new boolean[maxChars];
		gl2d.setFont(srcFont);
		FontMetrics metrics = gl2d.getFontMetrics();
		for (int i = 0; i < maxChars; i++) {
			int[] temp = new int[1];
			temp[0] = i;
			GlyphVector tempgv = srcFont.createGlyphVector(frc, temp);
			gv[i] = tempgv;
		}

		// Work out character mappings
		for (char i = 0; i < maxChars; i ++) {
			char[] charToMap = new char[] {i};
			GlyphVector tempgv = srcFont.createGlyphVector(frc, charToMap);
			mapping[i] = tempgv.getGlyphCode(0);
			if (i >= '0' && i <= '9') {
				fixedWidth[mapping[i]] = true;
			}
		}

		// Let's make a guess at what size we need by using 8*width of an M as the width
		// and fitting the characters in:
		int width = Util.nextPowerOf2((int) (8 * srcFont.getStringBounds("M", frc).getWidth()));
		int height = metrics.getHeight();
		int x = 0, y = 0, maxy = 0;
		for (int i = 0; i < maxChars; i++) {
			if (gv[i] == null) {
				continue;
			}
			Shape shape = gv[i].getGlyphOutline(0);
			java.awt.Rectangle bounds = shape.getBounds();

			if (bounds.width == 0 || bounds.height == 0) {
				continue;
			}

			// Start a new row if another character won't fit
			if (x + bounds.width + BORDER + (blur ? 2 : 0) > width) {
				x = 0;
				y += maxy / 2;
				System.out.println("Row height "+maxy+" new height now "+y);
				maxy = 0;
			}

			x += bounds.width + BORDER + (blur ? 2 : 0);
			maxy = Math.max(maxy, bounds.height + BORDER + (blur ? 2 : 0));

		}

		// Now round the height to a legal OpenGL power-of-2

		height = y+maxy/2;
		System.out.println("Adjusted to "+width+"x"+height+" max height "+(y+maxy));
		while (height >= width && width <= 512) {
			height *= 0.5;
			width *= 2;
		}
		height = Util.nextPowerOf2(y+maxy);
		System.out.println("Adjusted to "+width+"x"+height);

		width = Math.min(1024, width);
		height = Math.min(1024, height);

		// Create a buffered image of this size and set up the font again:
		BufferedImage image =
			new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR_PRE);
		Graphics2D g2d = (Graphics2D) image.getGraphics();
		g2d.setFont(srcFont);
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		int numGlyphs = Math.min(maxChars, srcFont.getNumGlyphs());
		Glyph[] glyph = new Glyph[numGlyphs];
		for (int i = 0; i < numGlyphs; i ++) {
			glyph[i] = new Glyph();
		}

		GLGraphics2D specialRenderer = new GLGraphics2D();
		x = 0;
		y = 0;

		for (int i = 0; i < numGlyphs; i++) {
			if (gv[i] == null) {
				continue;
			}
			Shape shape = gv[i].getGlyphOutline(0);

			java.awt.Rectangle bounds = shape.getBounds();

			// Because characters are drawn below and to the left of the "origin" (x,y)
			// we need to move them along a wee bit. The translation applied to the bounds
			// here moves the whole rectangle to a (0,0) origin
			int ox = bounds.x;
			int oy = bounds.y;

			// Start a new row if another character won't fit
			if (x + bounds.width + BORDER + (blur ? 2 : 0)  >= image.getWidth()) {
				x = 0;
				y += maxy;
				maxy = 0;
			}

			// Draw the glyph so that it doesn't go over any other characters already drawn,
			// by moving it back to a 0,0 origin
			g2d.translate((blur ? 1 : 0) + x - ox, (blur ? 1 : 0) + y - oy);
			g2d.fill(shape);
			if (DEBUG) {
//				g2d.setColor(Color.RED);
//				g2d.drawRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);
//				g2d.drawRect(bounds.x + 1, bounds.y + 1, bounds.width - 3, bounds.height - 3);
//				g2d.drawRect(bounds.x + 2, bounds.y + 2, bounds.width - 5, bounds.height - 5);
//				g2d.drawRect(bounds.x + 3, bounds.y + 3, bounds.width - 7, bounds.height - 7);
//
//				for (int xxx = 0; xxx < bounds.width - 5; xxx += 4) {
//					boolean draw = (xxx & 4) == 4;
//					for (int yyy = 0; yyy < bounds.height - 5; yyy += 4) {
//						draw = !draw;
//						if (draw) {
//							g2d.fillRect(xxx + bounds.x, yyy + bounds.y, 4, 4);
//						}
//					}
//				}
//
//				g2d.setColor(Color.WHITE);
			}
			g2d.translate((blur ? -1 : 0) - (x - ox), (blur ? -1 : 0) - (y - oy));

			GlyphMetrics gmetrics = gv[i].getGlyphMetrics(0);
			float glyphAdvance;
			if (fixedWidth[i]) {
				glyphAdvance = gv[mapping['0']].getGlyphMetrics(0).getAdvance();
				ox = (int)((glyphAdvance - bounds.width) / 2.0f);
			//	System.out.println(ox);
			} else {
				glyphAdvance = gmetrics.getAdvance();
			}

			// Calculate kerning with all other glyphs.
			ArrayList kerningList = new ArrayList();
			ArrayList kernsWithList = new ArrayList();

			for (int left = 0; left < numGlyphs; left ++) {
				if (gv[left] == null) {
					continue;
				}
				GlyphVector kerningVector = srcFont.createGlyphVector(frc, new int[] {left, i});
				specialRenderer.reset();
				specialRenderer.drawGlyphVector(kerningVector, 0.0f, 0.0f);
				GlyphMetrics gmetrics2 = gv[left].getGlyphMetrics(0);
				float glyphAdvance2 = gmetrics2.getAdvance();

				float xdif = (specialRenderer.xxx[1] - specialRenderer.xxx[0]);
				if (xdif != glyphAdvance2) {
					kernsWithList.add(glyph[left]);
					kerningList.add(new Integer((int)Math.rint(0.25 + xdif - glyphAdvance2)));
					//System.out.println(i + " kerns with "+left+" : "+ ((int) xdif - glyphAdvance));
				}
			}


			Glyph[] kernsWith;
			int[] kerning;

			if (kerningList.size() > 0) {
				kernsWith = new Glyph[kernsWithList.size()];
				kernsWithList.toArray(kernsWith);
				kerning = new int[kerningList.size()];
				for (int q = 0; q < kerningList.size(); q ++) {
					kerning[q] = ((Integer) kerningList.get(q)).intValue();
				}
			} else {
				kernsWith = null;
				kerning = null;
			}

			glyph[i].init
				(
					x - (blur ? 1 : 0),
					y - (blur ? 1 : 0),
					bounds.width + (blur ? 2 : 0),
					bounds.height + (blur ? 2 : 0),
					ox - (blur ? 1 : 0),
					(-(bounds.height + oy)) - (blur ? 1 : 0),
					(int) Math.floor(glyphAdvance),
					kernsWith,
					kerning
				);

			x += bounds.width + BORDER + (blur ? 2 : 0) ; // +2 just in case
			maxy = Math.max(maxy, bounds.height + BORDER + (blur ? 2 : 0));

		}

		// Blur the image
		BufferedImage blurredImage;
		if (blur) {
			float[] matrix =
				{
			        0.025f, 0.050f, 0.025f,
			        0.050f, 0.700f, 0.050f,
			        0.025f, 0.050f, 0.025f,
			    };

		    BufferedImageOp op = new ConvolveOp(new Kernel(3, 3, matrix));
			blurredImage = op.filter(image, null);
		} else {
			blurredImage = image;
		}

		// Now put buffered image back into font image
		int h = blurredImage.getHeight();
		while (y + maxy < h / 2) {
			h /= 2;
		}
		blurredImage = blurredImage.getSubimage(0, 0, blurredImage.getWidth(), h);
		byte[] newRenderedImage = (byte[]) blurredImage.getRaster().getDataElements(0, 0, blurredImage.getWidth(), blurredImage.getHeight(), null);

		destFont =
			new Font(
				srcFont.getName(),
				srcFont.isBold(),
				srcFont.isItalic(),
				new Image(blurredImage.getWidth(), blurredImage.getHeight(), Image.LUMINANCE_ALPHA),
				glyph,
				srcFont.getSize(),
				metrics.getMaxAscent(),
				metrics.getMaxDescent(),
				metrics.getLeading(),
				mapping);

		for (y = 0; y < h; y++) {
			for (x = 0; x < blurredImage.getWidth(); x++) {

				int pos = y * blurredImage.getWidth() * BORDER + x * BORDER;
				byte alpha = newRenderedImage[pos + 3];
				byte img = newRenderedImage[pos + 1];

				destFont.getImage().getData().put(img);
				destFont.getImage().getData().put(alpha);
			}
		}

		destFont.getImage().getData().rewind();

		if (DEBUG) {
			final BufferedImage img = blurredImage;
			new JFrame() {
				private static final long serialVersionUID = 1L;

				{
					addMouseListener(new MouseAdapter() {
						@Override
						public void mouseClicked(MouseEvent e) {
							dispose();
						}
					});
					setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
					setSize(img.getWidth() * 2, img.getHeight() * 2+ 31);
					setVisible(true);
				}

				/* (non-Javadoc)
				 * @see java.awt.Container#paint(java.awt.Graphics)
				 */
				@Override
				public void paint(Graphics g) {
					Graphics2D g2dDebug = (Graphics2D) g;
					g2dDebug.setColor(java.awt.Color.black);
					g2dDebug.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					g2dDebug.fillRect(0, 32, img.getWidth() * 2, img.getHeight() * 2);
					g2dDebug.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));
					g2dDebug.drawImage(img, 0, 31, img.getWidth() * 2, img.getHeight() * 2, this);
				}
			};
		}

		return destFont;
	}

	/**
	 * @return the created font
	 */
	public Font getFont() {
		return destFont;
	}


}
