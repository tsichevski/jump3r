package ru.wowa.jump3r.mp3;

import java.util.ArrayList;

import org.eclipse.jdt.annotation.Nullable;

import ru.wowa.jump3r.mp3.ID3Tag.MimeType;

public class ID3TagSpec {
  private static final int GENRE_NUM_UNKNOWN = 255;
	public ID3TagSpec() {
	  genre_id3v1 = GENRE_NUM_UNKNOWN;
    padding_size = 128;
  }
	
  int flags;
	int year;
	@Nullable String title;
	@Nullable String artist;
	@Nullable String album;
	@Nullable String comment;
	int track_id3v1;
	int genre_id3v1;
	byte @Nullable [] albumart;
	int albumart_size;
	int padding_size;
	@Nullable MimeType albumart_mimetype;
	ArrayList<String> values = new ArrayList<>();
	int num_values;
	@Nullable FrameDataNode v2_head, v2_tail;
}