package ru.wowa.jump3r.lowlevel;

import java.nio.ByteBuffer;

import ru.wowa.jump3r.mp3.BitStream;
import ru.wowa.jump3r.mp3.Enc;
import ru.wowa.jump3r.mp3.GainAnalysis;
import ru.wowa.jump3r.mp3.GetAudio;
import ru.wowa.jump3r.mp3.ID3Tag;
import ru.wowa.jump3r.mp3.Lame;
import ru.wowa.jump3r.mp3.LameGlobalFlags;
import ru.wowa.jump3r.mp3.Parse;
import ru.wowa.jump3r.mp3.Presets;
import ru.wowa.jump3r.mp3.Quantize;
import ru.wowa.jump3r.mp3.QuantizePVT;
import ru.wowa.jump3r.mp3.Reservoir;
import ru.wowa.jump3r.mp3.Takehiro;
import ru.wowa.jump3r.mp3.VBRTag;
import ru.wowa.jump3r.mp3.Version;
import ru.wowa.jump3r.mpg.Common;
import ru.wowa.jump3r.mpg.Interface;
import ru.wowa.jump3r.mpg.MPGLib;

public class LameDecoder {

  public LameDecoder(String mp3File) {
    // encoder modules
    GainAnalysis ga = new GainAnalysis();
    VBRTag vbr = new VBRTag();
    Version ver = new Version();
    Presets p = new Presets();

    Common common = new Common();
    Interface intf = new Interface(common);
    MPGLib mpg = new MPGLib(intf, common);

    BitStream bs = new BitStream(ga, mpg, ver, vbr);
    Reservoir rv = new Reservoir(bs);
    ID3Tag id3 = new ID3Tag(bs, ver);
    
    Takehiro tak = new Takehiro();
    QuantizePVT qupvt = new QuantizePVT(tak);
    Quantize qu = new Quantize(bs, rv, qupvt, tak);
    Lame lame = new Lame(ga, bs, p, qupvt, qu, vbr, ver, id3, mpg);
    tak.setModules(qupvt);
    vbr.setModules(lame, bs, ver);
    parse = new Parse(ver, id3, p);
    gaud = new GetAudio(parse, mpg);
    gfp = Lame.lame_init();

    /*
     * turn off automatic writing of ID3 tag data into mp3 stream we have to
     * call it before 'lame_init_params', because that function would spit
     * out ID3v2 tag data.
     */
    gfp.write_id3tag_automatic = false;

    /*
     * Now that all the options are set, lame needs to analyze them and set
     * some more internal options and check for problems
     */
    lame.lame_init_params(gfp);

    parse.input_format = GetAudio.sound_file_format.sf_mp3;

    StringBuilder inPath = new StringBuilder(mp3File);
    Enc enc = new Enc();

    gaud.init_infile(gfp, inPath.toString(), enc);

    int skip_start = 0;
    int skip_end = 0;

    if (parse.silent < 10)
      System.out.printf("\rinput:  %s%s(%g kHz, %d channel%s, ", inPath,
          inPath.length() > 26 ? "\n\t" : "  ",
          gfp.in_samplerate / 1.e3, gfp.num_channels,
          gfp.num_channels != 1 ? "s" : "");

    if (enc.enc_delay > -1 || enc.enc_padding > -1) {
      if (enc.enc_delay > -1)
        skip_start = enc.enc_delay + 528 + 1;
      if (enc.enc_padding > -1)
        skip_end = enc.enc_padding - (528 + 1);
    } else
      skip_start = gfp.encoder_delay + 528 + 1;

    System.out.printf("MPEG-%d%s Layer %s", 2 - gfp.version,
        gfp.out_samplerate < 16000 ? ".5" : "", "III");

    System.out.printf(")\noutput: (16 bit, Microsoft WAVE)\n");

    if (skip_start > 0)
      System.out.printf(
          "skipping initial %d samples (encoder+decoder delay)\n",
          skip_start);
    if (skip_end > 0)
      System.out
          .printf("skipping final %d samples (encoder padding-decoder delay)\n",
              skip_end);

    parse.mp3input_data.totalframes = parse.mp3input_data.nsamp
        / parse.mp3input_data.framesize;

    assert (gfp.num_channels >= 1 && gfp.num_channels <= 2);
  }

	public void decode(final ByteBuffer sampleBuffer, final boolean playOriginal) {
		int iread = gaud.get_audio16(gfp, buffer);
		if (iread >= 0) {
			parse.mp3input_data.framenum += iread
					/ parse.mp3input_data.framesize;

			for (int i = 0; i < iread; i++) {
				if (playOriginal) {
					// We put mp3 data into the sample buffer here!
					sampleBuffer.array()[i * 2] = (byte) (buffer[0][i] & 0xff);
					sampleBuffer.array()[i * 2 + 1] = (byte) (((buffer[0][i] & 0xffff) >> 8) & 0xff);
				}

				if (gfp.num_channels == 2) {
					// gaud.write16BitsLowHigh(outf, buffer[1][i] & 0xffff);
					// TODO two channels?
				}
			}
		}

	}

	public void close() {
		Lame.lame_close(gfp);
	}

  private GetAudio gaud;
	private Parse parse;
  private short buffer[][] = new short[2][1152];
  private LameGlobalFlags gfp;
}
