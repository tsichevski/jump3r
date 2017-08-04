package ru.wowa.jump3r.mp3;

import org.eclipse.jdt.annotation.Nullable;

public class FrameDataNode {
  @Nullable FrameDataNode nxt;
	/**
	 * Frame Identifier
	 */
	int fid;
	/**
	 * 3-character language descriptor
	 */
	String lng;

	Inf dsc = new Inf(), txt = new Inf();
}
