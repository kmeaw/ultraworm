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

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.net.URL;

import javax.imageio.ImageIO;

import org.lwjgl.opengl.GLContext;
import org.lwjgl.util.ReadableDimension;
import org.w3c.dom.Element;

import com.shavenpuppy.jglib.Image;
import com.shavenpuppy.jglib.Resource;
import com.shavenpuppy.jglib.resources.DimensionParser;
import com.shavenpuppy.jglib.resources.ImageWrapper;
import com.shavenpuppy.jglib.util.Util;
import com.shavenpuppy.jglib.util.XMLUtil;

/**
 * A special resource for loading images using ImageIO.
 *
 * @author foo
 */
public class ImageIOWrapper extends Resource implements ImageWrapper {

	public static final long serialVersionUID = 1L;

	/*
	 * Resource data
	 */

	/** The image to load */
	private String url;

	/** Fallback size if GL doesn't support ARB_texture_non_power_of_2 */
	private ReadableDimension fallbackSize;

	/**
	 * C'tor
	 */
	public ImageIOWrapper() {}

	/**
	 * Named c'tor
	 */
	public ImageIOWrapper(String name) {
		super(name);
	}

	/* (Overrides)
	 * @see com.shavenpuppy.jglib.Resource#load(org.w3c.dom.Element, com.shavenpuppy.jglib.Resource.Loader)
	 */
	@Override
    public void load(Element element, Loader loader) throws Exception {
		super.load(element, loader);

		url = XMLUtil.getString(element, "url");
		if (XMLUtil.hasAttribute(element, "fallbacksize")) {
			fallbackSize = DimensionParser.parse(XMLUtil.getString(element, "fallbacksize"));
		}
	}

	/* (Overrides)
	 * @see com.shavenpuppy.jglib.resources.ImageWrapper#getImage()
	 */
	@Override
    public Image getImage() throws Exception {

		BufferedImage image = ImageIO.read(new URL(url));

		// Construct the image on the fly and return it. If GL doesn't support ARB_texture_non_power_of_two
		// we have to stretch the image to fit into the fallback size.
		if (GLContext.getCapabilities().GL_ARB_texture_non_power_of_two) {
			return ImageConverter.convert(image, false);
		}
		int newWidth = Util.nextPowerOf2(image.getWidth());
		int newHeight = Util.nextPowerOf2(image.getHeight());
		if (newWidth == image.getWidth() && newHeight == image.getHeight()) {
			return ImageConverter.convert(image, false);
		}

		WritableRaster newRaster = image.getRaster().createCompatibleWritableRaster(newWidth, newHeight);
		BufferedImage newImage = new BufferedImage(image.getColorModel(), newRaster, false, null);
		Graphics2D g2d = (Graphics2D) newImage.getGraphics();
		g2d.drawImage(image, 0, 0, newWidth, newHeight, null);
		return ImageConverter.convert(newImage, false);

	}

}
