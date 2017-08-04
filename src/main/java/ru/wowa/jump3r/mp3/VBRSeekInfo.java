package ru.wowa.jump3r.mp3;

public class VBRSeekInfo {
	/**
	 * What we have seen so far.
	 */
	int sum;
	/**
	 * How many frames we have seen in this chunk.
	 */
	int seen;
	/**
	 * How many frames we want to collect into one chunk.
	 */
	int want;
	/**
	 * Actual position in our bag.
	 */
	int pos;
	/**
	 * Size of our bag.
	 */
	final static int BAG_SIZE = 400;
	/**
	 * Pointer to our bag.
	 */
	int[] bag = new int[BAG_SIZE];

	int nVbrNumFrames;
	int nBytesWritten;
	/* VBR tag data */
	int TotalFrameSize;
}