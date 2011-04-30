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
import java.awt.image.DataBuffer;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Iterator;

import javax.imageio.*;
import javax.imageio.stream.ImageOutputStream;

import com.shavenpuppy.jglib.Image;

/**
 * Java JPEG compressor for the Image class.
 */
public class JPEGCompressor implements Image.JPEGCompressor {

	/**
	 * C'tor
	 */
	public JPEGCompressor() {
	}

	/* (non-Javadoc)
	 * @see com.shavenpuppy.jglib.Image.JPEGCompressor#compress(int, int, java.nio.ByteBuffer)
	 */
	@Override
    public ByteBuffer compress(int width, int height, ByteBuffer src) throws Exception {
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);

		DataBuffer buf = img.getRaster().getDataBuffer();
		int count = 0;
		src.rewind();
		while (src.hasRemaining()) {
			buf.setElem(count ++, src.get());
		}
		ByteArrayOutputStream baos = new ByteArrayOutputStream(width * height * 3);
		ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
		for (Iterator writers = ImageIO.getImageWritersByFormatName("jpeg"); writers.hasNext(); ) {
			ImageWriter writer = (ImageWriter) writers.next();
			ImageWriteParam param = writer.getDefaultWriteParam();
			IIOImage iio_img = new IIOImage(img, null, null);
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(1.0f);
			writer.setOutput(ios);
			writer.write(null, iio_img, param);
			ByteBuffer dest = ByteBuffer.allocate(baos.size());
			dest.put(baos.toByteArray());
			dest.flip();
			return dest;
		}
		throw new Exception();
	}


/*	public static void main(String[] args) {
		ByteBuffer src = ByteBuffer.allocate(128 * 128 * 3);


		try {
			ByteBuffer dest = new JPEGCompressor().compress(128, 128, src);
			System.out.println("Shrink to "+dest.capacity());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
*/}