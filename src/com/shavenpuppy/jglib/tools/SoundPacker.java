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

import java.io.*;
import java.util.*;

import javax.sound.sampled.*;

import com.shavenpuppy.jglib.Wave;

/**
 * $Id: SoundPacker.java,v 1.17 2011/04/18 23:27:44 cix_foo Exp $
 *
 * The Sound Packer recursively collects .wav files in a directory and loads
 * their wave data into a single, large buffer and outputs an enormous Wave object.
 *
 * @author $Author: cix_foo $
 * @version $Revision: 1.17 $
 */
public class SoundPacker {

	/** The output */
	private OutputStream outputStream;

	/** Soundbank name, eg. mysounds.soundbank */
	private String soundBank;

	/** Frequency */
	private int frequency;

	/** Target frequency */
	private int targetFrequency;

	/** Current offset */
	private int offset;

	/** Format */
	private int format;

	/** Divisor for offset */
	private int divisor;

	/** Target format */
	private int targetFormat;

	/** Write the XML only, don't pack the sounds together */
	private boolean xmlonly;

	/** Add the "streamed" tag */
	private boolean streamed;

	/** OGGs */
	private List oggs = new ArrayList();

	/** Clips */
	private List clips = new ArrayList();

	/**
	 * A clip
	 */
	private static class Clip {
		final String name, soundBank;
		final int offset, len, divisor;
		Clip(String name, String soundBank, int offset, int len, int divisor) {
			this.name = name;
			this.soundBank = soundBank;
			this.offset = offset;
			this.len = len;
			this.divisor = divisor;
			System.out.println(name+" target size : "+(len >> divisor));
		}
		void writeXML(Writer writer) throws IOException {
			writer.write("\t\t<clip name=\""+name+"\" soundbank=\""+soundBank+"\" offset=\""+(offset >> divisor)+"\" length=\""+(len >> divisor)+"\"/>\n");
		}
	}

	/**
	 * An ogg
	 */
	private static class OGG {
		final String name;
		final boolean streamed;
		OGG(String name, boolean streamed) {
			this.name = name;
			this.streamed = streamed;
		}
		void writeXML(Writer writer) throws IOException {
			writer.write("\t<ogg name=\""+name+".ogg\" url=\"classpath:"+name+".ogg\" streamed=\""+streamed+"\"/>\n");
		}
	}

	private String decodeFormat() {
		switch (targetFormat) {
			case Wave.MONO_16BIT:
				return "MONO_16BIT";
			case Wave.MONO_8BIT:
				return "MONO_8BIT";
			case Wave.STEREO_16BIT:
				return "STEREO_16BIT";
			case Wave.STEREO_8BIT:
				return "STEREO_8BIT";
			default:
				assert false;
				return "Error - unknown format";
		}
	}

	/**
	 * Pack a directory
	 * @param dir The name of the directory
	 */
	private void pack(String dir) throws Exception {
		File fDir = new File(dir);
		if (fDir.isFile()) {
			doWave(fDir);
		} else {
			File[] files = fDir.listFiles();
			if (files == null) {
				return;
			}
			for (int i = 0; i < files.length; i ++) {
				if (files[i].isDirectory()) {
					System.out.println("------> Recursing into "+files[i]);
					pack(files[i].getPath());
				} else if (files[i].getName().endsWith(".wav")) {
					doWave(files[i]);
				}
			}
		}
	}

	/**
	 * Do a wave file
	 */
	private void doWave(File waveFile) throws Exception {
		System.out.print("Processing "+waveFile+".. ");
		AudioInputStream ais = AudioSystem.getAudioInputStream(new BufferedInputStream(new FileInputStream(waveFile)));
		AudioFormat audioFormat = ais.getFormat();

		if (audioFormat.getSampleRate() != frequency) {
			System.out.println("Wrong frequency - ignoring");
			return;
		}

		int type = 0;
		if (audioFormat.getChannels() == 1) {
			if (audioFormat.getSampleSizeInBits() == 8) {
				type = Wave.MONO_8BIT;
			} else if (audioFormat.getSampleSizeInBits() == 16) {
				type = Wave.MONO_16BIT;
			} else {
				assert false : "Illegal sample size"+audioFormat.getSampleSizeInBits()+" in "+waveFile;
			}
		} else if (audioFormat.getChannels() == 2) {
			if (audioFormat.getSampleSizeInBits() == 8) {
				type = Wave.STEREO_8BIT;
			} else if (audioFormat.getSampleSizeInBits() == 16) {
				type = Wave.STEREO_16BIT;
			} else {
				assert false : "Illegal sample size: "+audioFormat.getSampleSizeInBits()+" in "+waveFile;
			}
		} else {
			assert false : "Only mono or stereo is supported";
		}

		if (type != format) {
			System.out.println("Wrong format - skipping...");
			return;
		}

		int length = (int)(ais.getFrameLength() * audioFormat.getFrameSize());

		if (xmlonly) {
			// Simply remember this ogg
			int pos = waveFile.getCanonicalPath().lastIndexOf('.');
			int slashpos = waveFile.getCanonicalPath().lastIndexOf("\\");
			String name = waveFile.getCanonicalPath().substring(slashpos + 1, pos);
			OGG ogg = new OGG(name, streamed);
			oggs.add(ogg);
			System.out.println();
			return;
		}

/*
		System.out.println("Format : "+audioFormat);
		System.out.println(audioFormat.getChannels()+" channels");
		System.out.println(audioFormat.getFrameRate()+" frame rate");
		System.out.println(audioFormat.getFrameSize()+" frame size");
		System.out.println(audioFormat.getSampleRate()+" sample rate");
		System.out.println(audioFormat.getSampleSizeInBits()+" sample size");
*/
		byte[] buf = new byte[length];

		int read = 0, total = 0;
		while ( (read = ais.read(buf, total, buf.length - total)) != -1 && total < buf.length) {
			total += read;
//			System.out.println("Read "+total+" bytes of "+buf.length);
		}
		System.out.print(" length: "+buf.length+" .. ");

		System.out.println("\nTrimming - channels:"+audioFormat.getChannels()+" sampleSize:"+audioFormat.getSampleSizeInBits());
		// Trim zeros at the end
		if (audioFormat.getChannels() == 2) {
			if (audioFormat.getSampleSizeInBits() == 16) {
				assert buf.length % 4 == 0;
				buf = trim16S(buf, 100);
				assert buf.length % 4 == 0;
			} else if (audioFormat.getSampleSizeInBits() == 8) {
				assert buf.length % 2 == 0;
				buf = trimS(buf, 5);
				assert buf.length % 2 == 0;
			} else {
				assert false;
			}
		}
		else if (audioFormat.getChannels() == 1)
		{
			if (audioFormat.getSampleSizeInBits() == 16)
			{
				assert buf.length % 2 == 0;
				buf = trim16(buf, 100);
				assert buf.length % 2 == 0;
			}
			else if (audioFormat.getSampleSizeInBits() == 8)
			{
				buf = trim(buf, 5);
			}
			else
			{
				assert false;
			}
		}
		else
		{
			assert false;
		}
		System.out.println();

		int divTarg = frequency / targetFrequency;
		int targDiv = targetFrequency / frequency;
		int addition = 0, mask = 0xFFFFFFFF;
		switch (divTarg) {
			case 0:
				// Upsampling, so pad with extra data
				switch (targDiv) {
					case 0:
					case 1:
						assert false;
					case 2:
						// Add an extra sample
						addition = (audioFormat.getSampleSizeInBits() * audioFormat.getChannels()) / 8;
						break;
					case 4:
						// Add 3 extra samples
						addition = 3 * (audioFormat.getSampleSizeInBits() * audioFormat.getChannels()) / 8;
						break;
					default:
						assert false;
				}
			case 1:
				addition = 0;
				break;
			case 2:
				// Remove a sample
				mask = 0xFFFFFFF0;
				/*
				switch (format) {
					case Wave.STEREO_16BIT:
						mask = 0xFFFFFFF8;
						break;
					case Wave.STEREO_8BIT:
						mask = 0xFFFFFFFC;
						break;
					case Wave.MONO_16BIT:
						mask = 0xFFFFFFFC;
						break;
					case Wave.MONO_8BIT:
						mask = 0xFFFFFFFE;
						break;
					default:
						assert false;
				}
				*/
				break;
			case 4:
				mask = 0xFFFFFFF0;
				/*
				// Remove 3 samples
				switch (format) {
					case Wave.STEREO_16BIT:
						mask = 0xFFFFFFF0;
						break;
					case Wave.STEREO_8BIT:
						mask = 0xFFFFFFF8;
						break;
					case Wave.MONO_16BIT:
						mask = 0xFFFFFFF8;
						break;
					case Wave.MONO_8BIT:
						mask = 0xFFFFFFFC;
						break;
					default:
						assert false;
				}
				*/
				break;
		}
		int newLen = (buf.length & mask) + addition;
		if (newLen != buf.length) {
			System.out.println("Resized to "+newLen+" (mask "+Long.toString((mask & 0x7FFFFFFF), 16)+", addition="+addition);
			byte[] newBuf = new byte[newLen];
			System.arraycopy(buf, 0, newBuf, 0, newLen);
			buf = newBuf;
		}
		outputStream.write(buf);
		int pos = waveFile.getCanonicalPath().lastIndexOf('.');
		int slashpos = waveFile.getCanonicalPath().lastIndexOf("\\");
		String name = waveFile.getCanonicalPath().substring(slashpos + 1, pos) + ".soundclip";
		System.out.println(name);
		Clip clip = new Clip(name, soundBank, offset, buf.length, divisor);
		offset += buf.length;
		clips.add(clip);
	}

	byte[] trim(byte[] buf, int threshold) {
		for (int i = buf.length; --i >= 0; ) {
			if (buf[i] > threshold || buf[i] < -threshold) {
				if (i + 1 == buf.length) {
					System.out.print("No trimming needed");
					return buf;
				}
				byte[] ret = new byte[i + 1];
				System.arraycopy(buf, 0, ret, 0, i + 1);
				System.out.print("Trimmed down to "+(i+1)+" bytes");
				return ret;
			}
		}
		System.out.print("Warning: dummy sound");
		return new byte[2]; // Dummy sound
	}

	byte[] trim16(byte[] buf, int threshold) {
		for (int i = buf.length - 2; i >= 0; i -= 2) {
			int value = ((buf[i] << 8) | (0xFF & buf[i + 1]));
			if (value > threshold || value < -threshold) {
				if (i + 2 == buf.length) {
					System.out.print("No trimming needed");
					return buf;
				}
				byte[] ret = new byte[i + 2];
				System.arraycopy(buf, 0, ret, 0, i + 2);
				System.out.print("Trimmed down to "+(i+2)+" bytes");
				return ret;
			}
		}
		System.out.print("Warning: dummy sound");
		return new byte[4]; // Dummy sound
	}

	byte[] trimS(byte[] buf, int threshold) {
		for (int i = buf.length - 2; i >= 0; i -= 2) {
			if ((buf[i] > threshold || buf[i] < -threshold) || (buf[i + 1] > threshold || buf[i + 1] < -threshold)) {
				if (i + 2 == buf.length) {
					System.out.print("No trimming needed");
					return buf;
				}
				byte[] ret = new byte[i + 2];
				System.arraycopy(buf, 0, ret, 0, i + 2);
				System.out.print("Trimmed down to "+(i+2)+" bytes");
				return ret;
			}
		}
		System.out.print("Warning: dummy sound");
		return new byte[4]; // Dummy sound
	}

	byte[] trim16S(byte[] buf, int threshold) {
		for (int i = buf.length - 4; i >= 0; i -= 4) {
			int valueL = ((buf[i] << 8) | (0xFF & buf[i + 1]));
			int valueR = ((buf[i + 2] << 8) | (0xFF & buf[i + 3]));
			if (valueL > threshold || valueL < -threshold || valueR > threshold || valueR < -threshold) {
				if (i + 4 == buf.length) {
					System.out.print("No trimming needed");
					return buf;
				}
				byte[] ret = new byte[i + 4];
				System.arraycopy(buf, 0, ret, 0, i + 4);
				System.out.print("Trimmed down to "+(i+4)+" bytes");
				return ret;
			}
		}
		System.out.print("Warning: dummy sound");
		return new byte[8]; // Dummy sound
	}

	/**
	 * C'tor
	 */
	public SoundPacker(String rootDir, String outputDir, String output, String outputXML, int frequency, int targetFrequency, boolean stereo, boolean targetStereo, boolean eightBit, boolean xmlonly, boolean streamed) {
		try {

			System.out.println("Base output dir:"+outputDir);
			System.out.println("Output files:"+output+"|"+outputXML);

			this.frequency = frequency;
			this.targetFrequency = targetFrequency;
			this.xmlonly = xmlonly;
			this.streamed = streamed;

			if (stereo && eightBit) {
				format = Wave.STEREO_8BIT;
			} else if (stereo) {
				format = Wave.STEREO_16BIT;
			} else if (eightBit) {
				format = Wave.MONO_8BIT;
			} else {
				format = Wave.MONO_16BIT;
			}
			if (targetStereo && eightBit) {
				targetFormat = Wave.STEREO_8BIT;
			} else if (targetStereo) {
				targetFormat = Wave.STEREO_16BIT;
			} else if (eightBit) {
				targetFormat = Wave.MONO_8BIT;
			} else {
				targetFormat = Wave.MONO_16BIT;
			}

			this.soundBank = output + ".soundbank";

			int freqDiv = frequency / targetFrequency;
			int freqDiv2 = targetFrequency / frequency;
			switch (freqDiv) {
				case 0:
					switch (freqDiv2) {
						case 0:
						case 1:
							assert false;
							break;
						case 2:
							divisor = -1;
							break;
						case 4:
							divisor = -2;
							break;
						case 8:
							divisor = -3;
							break;
					}
					break;
				case 1:
					divisor = 0;
					break;
				case 2:
					divisor = 1;
					break;
				case 4:
					divisor = 2;
					break;
				case 8:
					divisor = 3;
					break;
			}

			if (stereo && !targetStereo) {
				divisor ++;
			} else if (!stereo && targetStereo) {
				divisor --;
			}
			String outputFilename = outputDir + File.separator + output+".raw";

			// Create the directory structure to the output file if it doesn't already exist
			File parentDir = new File(outputFilename).getAbsoluteFile().getParentFile();
			parentDir.mkdirs();

			if (!xmlonly) {
				outputStream = new BufferedOutputStream(new FileOutputStream(outputFilename));
			}
			pack(rootDir);
			if (!xmlonly) {
				outputStream.flush();
				outputStream.close();
			}
			BufferedWriter bw = new BufferedWriter(new FileWriter(outputDir + File.separator + outputXML));
			bw.write("<?xml version='1.0' encoding='utf-8'?>\n<resources>\n");
			if (xmlonly) {
				for (Iterator i = oggs.iterator(); i.hasNext(); ) {
					OGG ogg = (OGG) i.next();
					ogg.writeXML(bw);
				}
			} else {
//				bw.write("\t<ogg name=\""+output+".ogg\" url=\"classpath:"+output+".ogg\" length=\""+(offset >> divisor)+"\"/>\n");
				bw.write("\t<ogg name=\""+output+".ogg\" url=\"classpath:"+output+".ogg\" />\n");
				bw.write("\t<soundbank\n\t\tname=\""+soundBank+"\"\n");
				bw.write("\t\tformat=\""+decodeFormat()+"\"\n");
				bw.write("\t\tfrequency=\""+targetFrequency+"\"\n");
				bw.write("\t\turl=\"resource:"+output+".ogg\"\n\t>\n");
				for (Iterator i = clips.iterator(); i.hasNext(); ) {
					Clip clip = (Clip) i.next();
					clip.writeXML(bw);
				}
				bw.write("\t</soundbank>\n");
			}
			bw.write("</resources>\n");
			bw.flush();
			bw.close();
		} catch (Exception e) {
			e.printStackTrace(System.err);
		}
	}

	/**
	 * Usage:
	 * SoundPacker <root input dir> <output file> <output xml>
	 * @param args
	 */
	public static void main(String[] args) {
		if (args.length < 4) {
			System.err.println("Usage: ");
			System.err.println("\tSoundPacker <root input dir/file> <root output dir> <output file> <output xml> <frequency> <targetFrequency> [stereo/[stereo|mono]] [8bit] [xmlonly] [streamed]");
			System.exit(-1);
		}

		List lArgs = Arrays.asList(args);

		boolean stereo, targetStereo;
		stereo =
				lArgs.contains("stereo/mono")
			||	lArgs.contains("stereo/stereo")
			||	lArgs.contains("stereo");
		targetStereo =
				lArgs.contains("mono/stereo")
			||	lArgs.contains("stereo/stereo")
			||	lArgs.contains("stereo");


		new SoundPacker(args[0], args[1], args[2], args[3], Integer.parseInt(args[4]), Integer.parseInt(args[4]), stereo, targetStereo, lArgs.contains("8bit"), lArgs.contains("xmlonly"), lArgs.contains("streamed"));
	}
}
