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

import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.*;
import java.io.*;
import java.util.ArrayList;

import javax.imageio.ImageIO;

import com.shavenpuppy.jglib.Image;
import com.shavenpuppy.jglib.Palette;

/**
 * Convert all .png images to .jgimage files in the specified directory or the current working directory.
 * Usage:
 * ImageConverter [<source dir>] [<dest dir>]
 */
public class ImageConverter {

	/**
	 * Constructor for ImageConverter.
	 */
	public ImageConverter() {
		super();
	}

	public static void main(String[] args)
	{
		// Parse input arguments
		String dirName;
		if (args.length > 0)
		{
			dirName = args[0];
			if (dirName.startsWith("\"") && dirName.endsWith("\""))
			{
				dirName = dirName.substring(1, dirName.length() - 1);
			}
		} else {
			dirName = ".";
		}

		boolean jpeg = false;
		if (args.length > 1)
		{
			if (args[1].equals("jpeg"))
			{
				Image.setCompressor(new JPEGCompressor());
				jpeg = true;
			}
		}

		// Find all input files
		File inputDir = new File(dirName);
		File[] pngs = findPngs(inputDir);

		// Find our output directory
		String destDir = inputDir.getAbsolutePath().substring(0, inputDir.getAbsolutePath().length() - 1);
		if (args.length > 1)
		{
			destDir = args[jpeg ? 2 : 1];
		}
		System.out.println("Output to "+destDir);

		// Convert all input files into the output directory
		convertFiles(pngs, destDir, jpeg);
	}

	public static File[] findPngs(File dir)
	{
		File[] pngs = dir.listFiles(new FilenameFilter()
			{
				@Override
                public boolean accept(File directory, String name)
				{
					return name.endsWith(".png");
				}
			});

		return pngs;
	}

	public static File[] convertFiles(File[] inputFiles, String destDir, boolean isJpeg)
	{
		ArrayList outFiles = new ArrayList();

		for (int i = 0; i < inputFiles.length; i++)
		{
			File inputFile = inputFiles[i];
			try
			{
				BufferedImage bi = ImageIO.read(inputFile);
//				JFrame f = new JFrame(inputFile.toString());
//				JLabel l = new JLabel(new ImageIcon(bi));
//				f.add(l, BorderLayout.CENTER);
//				f.pack();
//				f.setVisible(true);
//				f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
				int pos = inputFile.getCanonicalPath().lastIndexOf('.');
				int slashpos = inputFile.getCanonicalPath().lastIndexOf(File.separator);
				String dest = destDir + inputFile.getCanonicalPath().substring(slashpos, pos) + ".jgimage";
				Image image = convert(bi, isJpeg);
				File destFile = new File(dest);
				destFile.getParentFile().mkdirs();
				Image.write(image, new BufferedOutputStream(new FileOutputStream(destFile)));

				outFiles.add(destFile);

				System.out.println("Converted "+inputFile);
			}
			catch (Exception e)
			{
				System.out.println("Failed to convert "+inputFile+" due to "+e);
				e.printStackTrace();
			}
		}

		File[] ret = new File[outFiles.size()];
		for (int i=0; i<ret.length; i++)
		{
			ret[i] = (File)outFiles.get(i);
		}
		return ret;
	}

	/**
	 * Converts a BufferedImage into an SPGL Image, ready for use with OpenGL.
	 * @param bi The input buffered image
	 * @param jpeg Whether to use jpeg compression
	 * @return an Image
	 * @throws Exception if the conversion fails
	 */
	public static Image convert(BufferedImage bi, boolean jpeg) throws Exception {
		BufferedImage newBI;
		int type;
		switch (bi.getColorModel().getNumComponents()) {
			case 1:
				type = Image.LUMINANCE;
				newBI = bi;
				break;
			case 2:
				type = Image.LUMINANCE_ALPHA;
				newBI = bi;
				break;
			case 3:
				{
					type = Image.RGB;
					ComponentColorModel ccm = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB), false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
					newBI = new BufferedImage(ccm, ccm.createCompatibleWritableRaster(bi.getWidth(), bi.getHeight()), false, null);
					newBI.setData(bi.getRaster());
				}
				break;
			case 4:
				{
					type = Image.RGBA;
					ComponentColorModel ccm = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB), true, false, Transparency.TRANSLUCENT, DataBuffer.TYPE_BYTE);
					newBI = new BufferedImage(ccm, ccm.createCompatibleWritableRaster(bi.getWidth(), bi.getHeight()), false, null);
					newBI.setData(bi.getRaster());
				}
				break;
			default:
				throw new Exception("Unsupported image type");
		}
		bi = newBI;


//		int type;
//		boolean useInts = false;
//		int numComponents = 3;
//		switch (bi.getType()) {
//			case BufferedImage.TYPE_3BYTE_BGR :
//				type = Image.BGR;
//				break;
//			case BufferedImage.TYPE_4BYTE_ABGR :
//				type = Image.RGBA; // getTypeForColorModel((ComponentColorModel) bi.getColorModel());
//				break;
//			case BufferedImage.TYPE_INT_ARGB:
//				type = Image.RGBA; // Will need conversion
//				useInts = true;
//				numComponents = 4;
//				break;
//			case BufferedImage.TYPE_INT_BGR:
//				type = Image.RGB; // will need conversion
//				useInts = true;
//				numComponents = 3;
//				break;
//			case BufferedImage.TYPE_BYTE_GRAY :
//				type = Image.LUMINANCE;
//				break;
//			case BufferedImage.TYPE_BYTE_INDEXED :
//				type = Image.PALETTED;
//				break;
//			case BufferedImage.TYPE_USHORT_555_RGB :
//				throw new Exception("Unsupported image format");
//			case BufferedImage.TYPE_CUSTOM :
//				ColorModel cm = bi.getColorModel();
//				switch (numComponents = cm.getNumComponents()) {
//					case 1:
//						type = Image.LUMINANCE;
//						break;
//					case 2:
//						type = Image.LUMINANCE_ALPHA;
//						break;
//					case 3:
//						{
//							DirectColorModel dcm = (DirectColorModel) cm;
//							if 	(
//									dcm.getRedMask() == 0xFF0000
//								&&	dcm.getGreenMask() == 0xFF00
//								&&	dcm.getBlueMask() == 0xFF
//								)
//							{
//								type = Image.RGB;
//							} else {
//								type = Image.BGR;
//							}
//						}
//						break;
//					case 4:
//						{
//							DirectColorModel dcm = (DirectColorModel) cm;
//							type = getTypeForColorModel(dcm);
//						}
//						break;
//					default:
//						throw new Exception("Unsupported number of color components.");
//				}
//				useInts = cm.getTransferType() == DataBuffer.TYPE_INT ? true : false;
//				break;
//			default :
//				throw new Exception("Unsupported image type "+bi.getType());
//		}
		Image image;
//		if (useInts) {
//			int[] data = (int[]) bi.getRaster().getDataElements(0, 0, bi.getWidth(), bi.getHeight(), null);
//			byte[] dataAsBytes = new byte[data.length * numComponents];
//			int p = 0;
//			for (int i = 0; i < data.length; i ++) {
//				dataAsBytes[p ++] = (byte) ((data[i] >> 0) & 0xFF);
//				dataAsBytes[p ++] = (byte) ((data[i] >> 8) & 0xFF);
//				dataAsBytes[p ++] = (byte) ((data[i] >> 16) & 0xFF);
//				if (numComponents == 4) {
//					dataAsBytes[p ++] = (byte) ((data[i] >> 24) & 0xFF);
//				}
//			}
//			image = new Image(bi.getWidth(), bi.getHeight(), type, dataAsBytes);
//		} else {
			byte[] data = (byte[]) bi.getRaster().getDataElements(0, 0, bi.getWidth(), bi.getHeight(), null);
			image = new Image(bi.getWidth(), bi.getHeight(), type, data);
//		}
		if (type == Image.PALETTED) {
			IndexColorModel icm = (IndexColorModel) bi.getColorModel();
			int[] palette = new int[icm.getMapSize()];
			icm.getRGBs(palette);
			// Annoyingly getRGBs returns ARGB format which GL doesn't support...
			// so we'll turn it into RGBA
			for (int j = 0; j < palette.length; j ++) {
					palette[j] =
						((palette[j] & 0x00FF0000) >> 16) // red
						| ((palette[j] & 0x0000FF00)) // green
						| ((palette[j] & 0x000000FF) << 16) // blue
						| ((palette[j] & 0xFF000000)); // alpha
			}

			Palette newP = new Palette(Palette.RGBA, palette);
			image.setPalette(newP);
		} else {
			image.setUseJPEG(jpeg);
		}
		return image;
	}

	private static int getTypeForColorModel(DirectColorModel dcm) throws Exception {
		if 	(
				dcm.getAlphaMask() == 0xFF000000
			&&	dcm.getRedMask() == 0xFF0000
			&&	dcm.getGreenMask() == 0xFF00
			&&	dcm.getBlueMask() == 0xFF
			)
		{
			return Image.ARGB;
		} else if
			(
				dcm.getAlphaMask() == 0xFF000000
			&&	dcm.getBlueMask() == 0xFF0000
			&&	dcm.getGreenMask() == 0xFF00
			&&	dcm.getRedMask() == 0xFF
			)
		{
			return Image.ABGR;
		} else if
			(
				dcm.getRedMask() == 0xFF000000
			&&	dcm.getGreenMask() == 0xFF0000
			&&	dcm.getBlueMask() == 0xFF00
			&&	dcm.getAlphaMask() == 0xFF
			)
		{
			return Image.RGBA;
		} else if
			(
				dcm.getBlueMask() == 0xFF000000
			&&	dcm.getGreenMask() == 0xFF0000
			&&	dcm.getRedMask() == 0xFF00
			&&	dcm.getAlphaMask() == 0xFF
			)
		{
			return Image.BGRA;
		} else {
			throw new Exception("Unsupported mask "+Integer.toHexString(dcm.getAlphaMask())+" "+Integer.toHexString(dcm.getRedMask())+
					" "+Integer.toHexString(dcm.getGreenMask())+" "+Integer.toHexString(dcm.getBlueMask()));
		}
	}
}
