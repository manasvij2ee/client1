/* DiskBackedQueue
 *
 * Created on Oct 16, 2003
 *
 * Copyright (C) 2003 Internet Archive.
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
 */
package net.javacoding.queue;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Iterator;
import java.util.LinkedList;
import net.javacoding.xsearch.IndexManager;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Queue which uses a DiskQueue ('tailQ') for spillover entries once a in-memory
 * LinkedList ('headQ') reaches a maximum size.
 * 
 * @author Gordon Mohr
 */
public class DiskBackedQueue implements Queue, Serializable {
	/**
	 * if all contents would leave head less than this percent full, discard the
	 * backing file(s)
	 */
	protected static final float DISCARD_BACKING_THRESHOLD = 0.25f;

	private final static Logger logger = LoggerFactory.getLogger(DiskBackedQueue.class);

	protected int headMax;

	protected LinkedList<Object> headQ;

	protected DiskQueue tailQ;

	protected String name;
	
	/**
	 * @param dir
	 * @param name
	 * @param reuse
	 *            whether to reuse any existing backing files
	 * @param headMax
	 * @throws IOException
	 * 
	 */
	public DiskBackedQueue(File dir, String name, boolean reuse, int headMax) throws IOException {
		this.headMax = headMax;
		this.name = name;
		this.headQ = new LinkedList<Object>();
		this.tailQ = new DiskQueue(dir, name, reuse);
	}

	/**
	 * @see org.archive.queue.Queue#enqueue(java.lang.Object)
	 */
	public void enqueue(Object o) {
		logger.trace(name + "(" + length() + "): " + o);
		if (length() < headMax) {
			fillHeadQ();
			headQ.addLast(o);
		} else {
			tailQ.enqueue(o);
		}
	}

	/**
	 * @see org.archive.queue.Queue#isEmpty()
	 */
	public boolean isEmpty() {
		return length() == 0;
	}

	/**
	 * @see org.archive.queue.Queue#dequeue()
	 */
	public Object dequeue() {
		Object retObj = null;
		if (headQ.isEmpty()) {
			// batch fill head if possible
			fillHeadQ();
		}
		if (headQ.isEmpty()) {
			// if still no memory head, get from backing
			retObj = backingDequeue();
		} else {
			// get from memory head where possible
			retObj = headQ.removeFirst();
		}
		logger.trace(name + "(" + length() + "): " + retObj);
		backingUpdate();
		return retObj;
	}

	protected void backingUpdate() {
		if (canDiscardBacking()) {
			discardBacking();
		}
	}

	protected void discardBacking() {
		// Flush out the items on disk and close the
		// files to free up file handles.
		fillHeadQ();
		if (tailQ.isEmpty()) {
			tailQ.release();
		}
	}

	/**
	 * Release any file-handles in arecoverable way.
	 */
	public void disconnect() {
		tailQ.disconnect();
	}

	/**
	 * @return True if can discard backing file.
	 */
	protected boolean canDiscardBacking() {
		// Check if less then a quarter of what can fit in the memory
		// cache is left in the queue.
		return length() <= headMax * DISCARD_BACKING_THRESHOLD && tailQ.isInitialized();
	}

	protected void fillHeadQ() {
		while (headQ.size() < headTargetSize() && headQ.size() < length()) {
			headQ.addLast(backingDequeue());
		}
	}

	/**
	 * @return backing dequeue queue instance.
	 */
	protected Object backingDequeue() {
		return tailQ.dequeue();
	}

	/**
	 * @return Maximum size of head queue.
	 */
	protected int headTargetSize() {
		return headMax;
	}

	/**
	 * @see org.archive.queue.Queue#length()
	 */
	public long length() {
		return headQ.size() + tailQ.length();
	}

	/**
	 * @see org.archive.queue.Queue#release()
	 */
	public void release() {
		tailQ.release();
	}

	/**
	 * @see org.archive.queue.Queue#peek()
	 */
	public Object peek() {
		if (headQ.isEmpty()) {
			fillHeadQ();
		}
		return (!headQ.isEmpty()) ? headQ.getFirst() : tailQ.peek();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.archive.queue.Queue#unpeek()
	 */
	public void unpeek() {
		// Nothing necessary; head of this queue is stable
	}

	/**
	 * @see org.archive.queue.Queue#getIterator(boolean)
	 */
	public Iterator<Object> getIterator(boolean inCacheOnly) {
		if (inCacheOnly) {
			// The headQ is a memory based structure and
			// that the tailQ is a disk based structure
			return headQ.iterator();
		} else {
			// Create and return a composite iterator over the two queues.
			Iterator<Object> it = new CompositeIterator(headQ.iterator(), tailQ.getIterator(false));

			return it;
		}
	}

	/**
	 * @see org.archive.queue.Queue#deleteMatchedItems(org.apache.commons.collections.Predicate)
	 */
	public long deleteMatchedItems(Predicate matcher) {
		long numberOfDeletes = 0;
		long oldSize = headQ.size();
		CollectionUtils.filter(headQ, new Inverter(matcher));
		numberOfDeletes += oldSize - headQ.size();
		numberOfDeletes += tailQ.deleteMatchedItems(matcher);
		return numberOfDeletes;
	}

	/**
	 * Set the maximum number of items to keep in memory at the structure's top.
	 * If more than that number are already in memory, they will remain in
	 * memory until dequeued, and thereafter the max will not be exceeded.
	 * 
	 * @param hm
	 *            Maximum number of items to keep in memory.
	 */
	public void setHeadMax(int hm) {
		headMax = hm;
	}
	
}
