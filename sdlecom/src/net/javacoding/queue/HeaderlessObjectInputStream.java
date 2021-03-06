/* Copyright (C) 2003 Internet Archive.
 *
 * This file is part of the Heritrix web crawler (crawler.archive.org).
 *
 * Heritrix is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * any later version.
 *
 * Heritrix is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser Public License
 * along with Heritrix; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * HeaderlessObjectInputStream.java
 * Created on May 21, 2004
 *
 * $Header$
 */
package net.javacoding.queue;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.StreamCorruptedException;


/**
 * Block read of java serialization stream header.
 *
 * Default java serialization writes a header of magic bytes, version and
 * length at start of a serialization stream.  This class matches the
 * {@link HeaderlessObjectOutputStream} which blocks the writing of the
 * serialization header; without it the default implementation would complain
 * about the header absence.
 *
 * <p>Used when want more control of serialization stream.
 */
public class HeaderlessObjectInputStream extends ObjectInputStream {

	public HeaderlessObjectInputStream(InputStream in) throws IOException {
		super(in);
	}

	protected void readStreamHeader()
		throws IOException, StreamCorruptedException {
		// do nothing
	}
}
