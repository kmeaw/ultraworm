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

import java.awt.image.BufferedImage;
import java.io.FileFilter;
import java.util.*;

import org.lwjgl.util.Rectangle;

/**
 * @author John Campbell
 */
public class ImageFilterDelegator implements ImageFilter {
	private ImageFilter filter;

	private static HashMap standardFilters;
	static {
		standardFilters = new HashMap();
		standardFilters.put("sprites", new NullFilter());
		standardFilters.put("shadows", new ShadowFilter());
		standardFilters.put("deformMap", new DeformMapFilter());
	}

	public ImageFilterDelegator(String imageFilterName) {
		if (imageFilterName != null) {
			// Check standard filters
			Iterator it = standardFilters.keySet().iterator();
			while (it.hasNext()) {
				String name = (String) it.next();
				if (imageFilterName.equals(name)) {
					filter = (ImageFilter) standardFilters.get(name);
					break;
				}
			}

			// Check for pluggable class
			if (filter == null) {
				try {
					Class c = Class.forName(imageFilterName);
					Object o = c.newInstance();

					if (o instanceof ImageFilter) {
						filter = (ImageFilter) o;
					}
				} catch (ClassNotFoundException e) {
					System.out.println("ImageFilter " + imageFilterName + " not found.");
					e.printStackTrace();
				} catch (InstantiationException e) {
					System.out.println("ImageFilter " + imageFilterName + " cannot be created.");
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					System.out.println("ImageFilter " + imageFilterName + " not avalible.");
					e.printStackTrace();
				}
			}
		}

		if (filter == null) {
			filter = new NullFilter();
		}

		System.out.println("Using filter:" + filter);
	}

	@Override
    public BufferedImage process(BufferedImage inImage, Rectangle offset) {
		return filter.process(inImage, offset);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.shavenpuppy.jglib.tools.ImageFilter#getFileFilter()
	 */
	@Override
    public FileFilter getFileFilter() {
		return filter.getFileFilter();
	}

	@Override
    public ArrayList preProcess(ArrayList loadedImages) {
		return filter.preProcess(loadedImages);
	}
}
