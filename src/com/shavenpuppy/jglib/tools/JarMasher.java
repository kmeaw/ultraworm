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
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.*;

/**
 * The Jar Masher takes as its input an rt.jar and a list of loaded classes from a -verbose:class dump. It then outputs a new rt.jar
 * which contains only those classes referenced in the class dump.
 */
public class JarMasher {

	/**
	 * Usage: java jarMasher source=rt.jar dest=new-rt.jar classlist=classes.txt [verbose=true] [overwrite=false]
	 * @param args
	 */
	public static void main(String[] args) {
		String source = parse(args, "source");
		String dest = parse(args, "dest");
		String classList = parse(args, "classlist");
		String verbose = parse(args, "verbose", "true");
		String overwrite = parse(args, "overwrite", "false");
		String includeList = parse(args, "include");

		if (source == null || dest == null || classList == null) {
			printUsage();
			System.exit(-1);
			return;
		}

		try {
			new JarMasher(source, dest, classList, includeList, Boolean.parseBoolean(verbose), Boolean.parseBoolean(overwrite)).process();
		} catch (IOException e) {
			e.printStackTrace(System.err);
			System.exit(-1);
		}
	}

	private static void printUsage() {
		System.err.println("Usage:\n\tjava jarMasher source=<path> dest=<path> classlist=<path> [includelist=[<path>]] [verbose=true] [overwrite=false]");
	}

	private static String parse(String[] args, String key) {
		return parse(args, key, null);
	}

	private static String parse(String[] args, String match, String default_) {
		for (String arg : args) {
			int idx = arg.indexOf('=');
			if (idx > 0) {
				String key = arg.substring(0, idx);
				String value = arg.substring(idx + 1);
				if (key.equalsIgnoreCase(match)) {
					if (value.startsWith("\"") && value.endsWith("\"")) {
						value = value.substring(1, value.length() - 2);
					}
					return value;
				}
			}
		}
		return default_;
	}


	private final File sourceFile;
	private final File destFile;
	private final File classListFile;
	private final File includeListFile;
	private final boolean verbose;
	private final boolean overwrite;

	public JarMasher(String source, String dest, String classList, String includeList, boolean verbose, boolean overwrite) {
		this.sourceFile = new File(source);
		this.destFile = new File(dest);
		this.classListFile = new File(classList);
		this.includeListFile = includeList == null ? null : new File(includeList);
		this.verbose = verbose;
		this.overwrite = overwrite;
	}

	public void process() throws IOException {
		// Check files exist
		if (!sourceFile.exists()) {
			throw new RuntimeException("source file does not exist");
		}
		if (!classListFile.exists()) {
			throw new RuntimeException("classlist file does not exist");
		}
		if (sourceFile.equals(destFile)) {
			throw new RuntimeException("source file must not be the same as dest file");
		}
		if (destFile.exists() && !overwrite) {
			throw new RuntimeException("dest file exists and overwrite=false; specify overwrite=true");
		}

		// Read the classlist file first
		Set<String> classesUsed = loadClassList();

		// Load include list, if present
		Set<String> packagesIncluded = loadIncludeList();

		FileOutputStream fos = new FileOutputStream(destFile);
		BufferedOutputStream bos = new BufferedOutputStream(fos, 65536);
		ZipOutputStream zos = new ZipOutputStream(bos);

		// Find out what's in the source jar file
		JarFile sourceJarFile = new JarFile(sourceFile, false, ZipFile.OPEN_READ);
		for (Enumeration<JarEntry> entries = sourceJarFile.entries(); entries.hasMoreElements(); ) {
			JarEntry entry = entries.nextElement();
			String entryName = entry.getName();
			boolean add = false;
			if (entryName.endsWith(".class")) {
				String className = entryName.substring(0, entryName.length() - 6).replace('/', '.');
				if (!classesUsed.contains(className)) {
					// Check if it's in an included package
					int idx = className.lastIndexOf('.');
					while (idx != -1) {
						String packageName = className.substring(0, idx);
						if (packagesIncluded.contains(packageName)) {
							add = true;
							break;
						} else {
							idx = packageName.lastIndexOf('.');
						}
					}
				} else {
					add = true;
				}
			} else {
				add = true;
			}

			if (add) {
				System.out.println("Adding "+entry);
				// Read the zip entry out of the source and stick it in the dest
				InputStream src = sourceJarFile.getInputStream(entry);
				ZipEntry out = new ZipEntry(entry);
				zos.putNextEntry(out);
				byte[] buf = new byte[(int) entry.getSize()];
				int read = -1;
				while ((read = src.read(buf)) != -1) {
					zos.write(buf, 0, read);
				}
				zos.closeEntry();
			}
		}


		zos.flush();
		zos.close();

	}

	private Set<String> loadClassList() throws IOException {
		FileReader fr = new FileReader(classListFile);
		BufferedReader br = new BufferedReader(fr);
		TreeSet<String> ret = new TreeSet<String>();
		String line;
		while ((line = br.readLine()) != null) {
			if (line.startsWith("[Loaded ")) {
				int idx = line.indexOf(' ', 8);
				if (idx != -1) {
					String className = line.substring(8, idx);
					ret.add(className);
				}
			}
		}

		return ret;
	}

	private Set<String> loadIncludeList() throws IOException {
		if (includeListFile == null || !includeListFile.exists()) {
			return new HashSet<String>(0);
		}
		FileReader fr = new FileReader(includeListFile);
		BufferedReader br = new BufferedReader(fr);
		TreeSet<String> ret = new TreeSet<String>();
		String line;
		while ((line = br.readLine()) != null) {
			line = line.trim();
			if (line.startsWith("#") || line.startsWith("//")) {
				continue;
			}
			ret.add(line);
		}

		return ret;
	}

}
