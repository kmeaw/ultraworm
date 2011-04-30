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

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.image.*;
import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;

import org.lwjgl.util.Rectangle;


/**
 * @author John Campbell
 */
public class DeformMapFilter implements ImageFilter
{

	@Override
    public BufferedImage process(BufferedImage inImage, Rectangle rectangle)
	{
		return inImage;
	}

	/** Takes a loaded list of buffered images and creates seperated deform maps.
	 */
	@Override
    public ArrayList preProcess(ArrayList loadedImages)
	{
		ArrayList retList = new ArrayList();

		for (int i=0; i<loadedImages.size(); i++)
		{
			LoadedImage original = (LoadedImage)loadedImages.get(i);

			BufferedImage addImg = createSeperatedDeformMap(original.img, true);
			retList.add( new LoadedImage(original.name+"_add", original.dir, addImg) );

			BufferedImage subImg = createSeperatedDeformMap(original.img, false);
			retList.add( new LoadedImage(original.name+"_sub", original.dir, subImg) );
		}

		return retList;
	}

	/** Accepts an input heightmap (greyscale) and outputs a buffered image for one half of a
	 *  deform map. The outputed deform map contains either the +ve or -ve components depending
	 *  on the flag set.
	 */
	public BufferedImage createSeperatedDeformMap(BufferedImage inImage, boolean isAdd)
	{
		// Make a copy of the image (via a null filter)
		AffineTransformOp op = new AffineTransformOp(new AffineTransform(), AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
		BufferedImage outImage = op.filter(inImage, null);

		Raster data = inImage.getData();

		for (int x=0; x<inImage.getWidth(); x++)
		{
			for (int y=0; y<inImage.getHeight(); y++)
			{
				// Samples taken:
				//  C
				//  A B
				//		where 'A' is current pixel.

				int heightA = data.getSample(x, y, 0);

				int heightB;
				if (x+1 != inImage.getWidth()) {
	                heightB = data.getSample(x+1, y, 0);
                } else {
	                heightB = data.getSample(0, y, 0);
                }

				int heightC;
				if (y+1 != inImage.getHeight()) {
	                heightC = data.getSample(x, y+1, 0);
                } else {
	                heightC = data.getSample(x, 0, 0);
                }

				int dx = heightB - heightA;
				int dy = heightC - heightA;

				// Resulting dx & dy are in range [-255, +255].

				Color c;
				if (isAdd)
				{
					// Clamp to only positive values
					dx = (dx > 0) ? dx : 0;
					dy = (dy > 0) ? dy : 0;

					c = new Color(dx, dy, heightA, 255);
				}
				else
				{
					// Clamp to only negative values, yet mapped to positive values
					dx = (dx < 0) ? -dx : 0;
					dy = (dy < 0) ? -dy : 0;

					c = new Color(dx, dy, 0, 255);
				}
				outImage.setRGB(x, y, c.getRGB());
			}
		}

		return outImage;
	}
	/* (non-Javadoc)
	 * @see com.shavenpuppy.jglib.tools.ImageFilter#getFileFilter()
	 */
	@Override
    public FileFilter getFileFilter() {
		return new FileFilter() {
			@Override
            public boolean accept(File pathname) {
				return true;
			}
		};
	}

}
