package ru.wowa.jump3r.mp3;

public class HuffCodeTab {
	public HuffCodeTab(int len, int max, int[] tab, int[] hl) {
		xlen = len;
		linmax = max;
		table = tab;
		hlen = hl;
	}

	/**
	 * max. x-index+
	 */
	int xlen;
	/**
	 * max number to be stored in linbits
	 */
	int linmax;
	/**
	 * pointer to array[xlen][ylen]
	 */
	int[] table;
	/**
	 * pointer to array[xlen][ylen]
	 */
	int[] hlen;
}
