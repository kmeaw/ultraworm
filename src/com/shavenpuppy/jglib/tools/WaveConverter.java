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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import com.shavenpuppy.jglib.Wave;

/**
 * Convert all .wav images to .jgwave files in the specified directory or the current working directory.
 * Usage:
 * WaveConverter [<source dir>] [<dest dir>]
 */
public class WaveConverter {

	/**
	 * Constructor for WaveConverter.
	 */
	public WaveConverter() {
		super();
	}

	public static void main(String[] args) {

		String dirName;
		if (args.length > 0) {
			dirName = args[0];
			if (dirName.startsWith("\"") && dirName.endsWith("\"")) {
				dirName = dirName.substring(1, dirName.length() - 1);
			}
		} else
			dirName = ".";

		File dir = new File(dirName);
		File[] wavs = dir.listFiles(new FilenameFilter() {
			public boolean accept(File directory, String name) {
				return name.endsWith(".wav");
			}
		});
		
		String destDir = dir.getAbsolutePath().substring(0, dir.getAbsolutePath().length() - 1);
		if (args.length == 2) {
			destDir = args[1];
		}
		System.out.println("Output to "+destDir);
	

		for (int i = 0; i < wavs.length; i++) {
			try {
				
				AudioInputStream ais = AudioSystem.getAudioInputStream(new BufferedInputStream(new FileInputStream(wavs[i])));
				AudioFormat format = ais.getFormat();
				
				long length = ais.getFrameLength();
				System.out.println("Format : "+format+" Frames: "+length);
				System.out.println("File size: "+wavs[i].length());
				System.out.println(format.getChannels()+" channels");
				System.out.println(format.getFrameRate()+" frame rate");
				System.out.println(format.getFrameSize()+" frame size");
				System.out.println(format.getSampleRate()+" sample rate");
				System.out.println(format.getSampleSizeInBits()+" sample size");
				
				int type = 0;
				if (format.getChannels() == 1) {
					if (format.getSampleSizeInBits() == 8)
						type = Wave.MONO_8BIT;
					else if (format.getSampleSizeInBits() == 16)
						type = Wave.MONO_16BIT;
					else
						assert false : "Illegal sample size";
				} else if (format.getChannels() == 2) {
					if (format.getSampleSizeInBits() == 8)
						type = Wave.STEREO_8BIT;
					else if (format.getSampleSizeInBits() == 16)
						type = Wave.STEREO_16BIT;
					else
						assert false : "Illegal sample size";
				} else
					assert false : "Only mono or stereo is supported";
				
				byte[] buf = new byte[(int)(ais.getFrameLength() * format.getFrameSize())];
				
				int read = 0, total = 0;
				while ( (read = ais.read(buf, total, buf.length - total)) != -1 && total < buf.length) {
					total += read;
					System.out.println("Read "+total+" bytes of "+buf.length);					
				}
				
				Wave wave = new Wave(
					(int) (ais.getFrameLength()),
					type,
					(int)format.getSampleRate(), 
					buf
				);
				
				
				ais.close();
				
				
				int pos = wavs[i].getCanonicalPath().lastIndexOf('.');
				int slashpos = wavs[i].getCanonicalPath().lastIndexOf("\\");
				String dest = destDir + wavs[i].getCanonicalPath().substring(slashpos, pos) + ".jgwave";
				Wave.write(wave, new BufferedOutputStream(new FileOutputStream(new File(dest))));
				System.out.println("Converted " + wavs[i]);
			} catch (Exception e) {
				System.out.println("Failed to convert "+wavs[i]+" due to "+e);
				e.printStackTrace();
			}
		}

	}
}
