/*
 *      Command line frontend program
 *
 *      Copyright (c) 1999 Mark Taylor
 *                    2000 Takehiro TOMINAGA
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */

/* $Id: Main.java,v 1.38 2011/08/27 18:57:12 kenchis Exp $ */

package ru.wowa.jump3r;

import java.beans.PropertyChangeSupport;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Locale;
import java.util.StringTokenizer;

import org.eclipse.jdt.annotation.NonNull;

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
import ru.wowa.jump3r.mp3.GetAudio.sound_file_format;
import ru.wowa.jump3r.mpg.Common;
import ru.wowa.jump3r.mpg.Interface;
import ru.wowa.jump3r.mpg.MPGLib;

public class Main {

  private static GetAudio gaud;
  private static Lame lame;
  private static Parse parse;

  private static PropertyChangeSupport support = new PropertyChangeSupport(
      Main.class);

  private static DataOutputStream init_files(LameGlobalFlags gf,
      String inPath, String outPath, Enc enc) throws FileNotFoundException {
    /*
     * Mostly it is not useful to use the same input and output name. This test
     * is very easy and buggy and don't recognize different names assigning the
     * same file
     */
    if (inPath.equals(outPath))
      throw new IllegalArgumentException("Input file and Output file are the same. Abort.");

    /*
     * open the wav/aiff/raw pcm or mp3 input file. This call will open the
     * file, try to parse the headers and set gf.samplerate, gf.num_channels,
     * gf.num_samples. if you want to do your own file input, skip this call and
     * set samplerate, num_channels and num_samples yourself.
     */
    gaud.init_infile(gf, inPath, enc);

    // FIXME: this overwrites output file silently
    new File(outPath).delete();
    return new DataOutputStream(new BufferedOutputStream(new FileOutputStream(outPath), 1<<20));
  }

  /**
   * the simple lame decoder
   * 
   * After calling lame_init(), lame_init_params() and init_infile(), call this
   * routine to read the input MP3 file and output .wav data to the specified
   * file pointer
   * 
   * lame_decoder will ignore the first 528 samples, since these samples
   * represent the mpglib delay (and are all 0). skip = number of additional
   * samples to skip, to (for example) compensate for the encoder delay
   */
  private static void lame_decoder(LameGlobalFlags gfp, DataOutput outf,
      int skip_start, String inPath, String outPath, Enc enc)
      throws IOException {
    short Buffer[][] = new short[2][1152];
    int iread;
    int skip_end = 0;
    int i;
    int tmp_num_channels = gfp.num_channels;

    if (parse.silent < 10)
      System.out.printf("\rinput:  %s%s(%g kHz, %d channel%s, ", inPath,
          inPath.length() > 26 ? "\n\t" : "  ", gfp.in_samplerate / 1.e3,
          tmp_num_channels, tmp_num_channels != 1 ? "s" : "");

    switch (parse.input_format) {
    case sf_mp123: /* FIXME: !!! */
      throw new RuntimeException("Internal error.  Aborting.");

    case sf_mp3:
      if (skip_start == 0) {
        if (enc.enc_delay > -1 || enc.enc_padding > -1) {
          if (enc.enc_delay > -1)
            skip_start = enc.enc_delay + 528 + 1;
          if (enc.enc_padding > -1)
            skip_end = enc.enc_padding - (528 + 1);
        } else
          skip_start = gfp.encoder_delay + 528 + 1;
      } else {
        /* user specified a value of skip. just add for decoder */
        skip_start += 528 + 1;
        /*
         * mp3 decoder has a 528 sample delay, plus user supplied "skip"
         */
      }

      if (parse.silent < 10)
        System.out.printf("MPEG-%d%s Layer %s", 2 - gfp.version,
            gfp.out_samplerate < 16000 ? ".5" : "", "III");
      break;
    case sf_mp2:
      skip_start += 240 + 1;
      if (parse.silent < 10)
        System.out.printf("MPEG-%d%s Layer %s", 2 - gfp.version,
            gfp.out_samplerate < 16000 ? ".5" : "", "II");
      break;
    case sf_mp1:
      skip_start += 240 + 1;
      if (parse.silent < 10)
        System.out.printf("MPEG-%d%s Layer %s", 2 - gfp.version,
            gfp.out_samplerate < 16000 ? ".5" : "", "I");
      break;
    case sf_raw:
      if (parse.silent < 10)
        System.out.printf("raw PCM data");
      parse.mp3input_data.nsamp = gfp.num_samples;
      parse.mp3input_data.framesize = 1152;
      skip_start = 0;
      /* other formats have no delay */
      break;
    case sf_wave:
      if (parse.silent < 10)
        System.out.printf("Microsoft WAVE");
      parse.mp3input_data.nsamp = gfp.num_samples;
      parse.mp3input_data.framesize = 1152;
      skip_start = 0;
      /* other formats have no delay */
      break;
    case sf_aiff:
      if (parse.silent < 10)
        System.out.printf("SGI/Apple AIFF");
      parse.mp3input_data.nsamp = gfp.num_samples;
      parse.mp3input_data.framesize = 1152;
      skip_start = 0;
      /* other formats have no delay */
      break;
    default:
      if (parse.silent < 10)
        System.out.printf("unknown");
      parse.mp3input_data.nsamp = gfp.num_samples;
      parse.mp3input_data.framesize = 1152;
      skip_start = 0;
      /* other formats have no delay */
      assert (false);
      break;
    }

    if (parse.silent < 10) {
      System.out.printf(")\noutput: %s%s(16 bit, Microsoft WAVE)\n", outPath,
          outPath.length() > 45 ? "\n\t" : "  ");

      if (skip_start > 0)
        System.out.printf(
            "skipping initial %d samples (encoder+decoder delay)\n",
            skip_start);
      if (skip_end > 0)
        System.out.printf(
            "skipping final %d samples (encoder padding-decoder delay)\n",
            skip_end);
    }

    if (parse.silent <= 0) {
      System.out.print("|");
      for (int j = 0; j < MAX_WIDTH - 2; j++) {
        System.out.print("=");
      }
      System.out.println("|");
    }
    oldPercent = curPercent = oldConsoleX = 0;

    if (!parse.disable_wav_header)
      GetAudio.WriteWaveHeader(outf, Integer.MAX_VALUE, gfp.in_samplerate,
          tmp_num_channels, 16);
    /* unknown BAG_SIZE, so write maximum 32 bit signed value */

    double wavsize = -(skip_start + skip_end);
    parse.mp3input_data.totalframes = parse.mp3input_data.nsamp
        / parse.mp3input_data.framesize;

    assert (tmp_num_channels >= 1 && tmp_num_channels <= 2);

    do {
      iread = gaud.get_audio16(gfp, Buffer);
      /* read in 'iread' samples */
      if (iread >= 0) {
        parse.mp3input_data.framenum += iread / parse.mp3input_data.framesize;
        wavsize += iread;

        if (parse.silent <= 0) {
          timestatus(parse.mp3input_data.framenum,
              parse.mp3input_data.totalframes);
        }

        skip_start -= (i = skip_start < iread ? skip_start : iread);
        /*
         * 'i' samples are to skip in this frame
         */

        if (skip_end > 1152 && parse.mp3input_data.framenum
            + 2 > parse.mp3input_data.totalframes) {
          iread -= (skip_end - 1152);
          skip_end = 1152;
        } else if (parse.mp3input_data.framenum == parse.mp3input_data.totalframes
            && iread != 0)
          iread -= skip_end;

        for (; i < iread; i++) {
          if (parse.disable_wav_header) {
            if (parse.swapbytes) {
              WriteBytesSwapped(outf, Buffer[0], i);
            } else {
              writeBytes(outf, Buffer[0], i);
            }
            if (tmp_num_channels == 2) {
              if (parse.swapbytes) {
                WriteBytesSwapped(outf, Buffer[1], i);
              } else {
                writeBytes(outf, Buffer[1], i);
              }
            }
          } else {
            GetAudio.write16BitsLowHigh(outf, Buffer[0][i] & 0xffff);
            if (tmp_num_channels == 2)
              GetAudio.write16BitsLowHigh(outf, Buffer[1][i] & 0xffff);
          }
        }
      }
    } while (iread > 0);

    i = (16 / 8) * tmp_num_channels;
    assert (i > 0);
    if (wavsize <= 0) {
      if (parse.silent < 10)
        System.err.println("WAVE file contains 0 PCM samples");
      wavsize = 0;
    } else if (wavsize > 0xFFFFFFD0L / i) {
      if (parse.silent < 10)
        System.err
            .println("Very huge WAVE file, can't set filesize accordingly");
      wavsize = 0xFFFFFFD0;
    } else {
      wavsize *= i;
    }

    ((Closeable) outf).close();
    /* if outf is seekable, rewind and adjust length */
    if (!parse.disable_wav_header) {
      try (RandomAccessFile rf = new RandomAccessFile(outPath, "rw")) {
        GetAudio.WriteWaveHeader(rf, (int) wavsize, gfp.in_samplerate,
            tmp_num_channels, 16);
      }
    }

    if (parse.silent <= 0) {
      System.out.print("|");
      for (int j = 0; j < MAX_WIDTH - 2; j++) {
        System.out.print("=");
      }
      System.out.println("|");
    }
  }

  private static void print_lame_tag_leading_info(LameGlobalFlags gf) {
    if (gf.bWriteVbrTag)
      System.out.println("Writing LAME Tag...");
  }

  private static void print_trailing_info(LameGlobalFlags gf) {
    if (gf.bWriteVbrTag)
      System.out.println("done\n");

    if (gf.findReplayGain) {
      int RadioGain = gf.internal_flags.RadioGain;
      System.out.printf("ReplayGain: %s%.1fdB\n", RadioGain > 0 ? "+" : "",
          (RadioGain) / 10.0f);
      if (RadioGain > 0x1FE || RadioGain < -0x1FE)
        System.out.println(
            "WARNING: ReplayGain exceeds the -51dB to +51dB range. Such a result is too\n"
                + "         high to be stored in the header.");
    }

    /*
     * if (the user requested printing info about clipping) and (decoding on the
     * fly has actually been performed)
     */
    if (parse.print_clipping_info && gf.decode_on_the_fly) {
      float noclipGainChange = gf.internal_flags.noclipGainChange / 10.0f;
      float noclipScale = gf.internal_flags.noclipScale;

      if (noclipGainChange > 0.0) {
        /* clipping occurs */
        System.out.printf(
            "WARNING: clipping occurs at the current gain. Set your decoder to decrease\n"
                + "         the  gain  by  at least %.1fdB or encode again ",
            noclipGainChange);

        /* advice the user on the scale factor */
        if (noclipScale > 0) {
          System.out.printf(Locale.US, "using  --scale %.2f\n", noclipScale);
          System.out.print(
              "         or less (the value under --scale is approximate).\n");
        } else {
          /*
           * the user specified his own scale factor. We could suggest the scale
           * factor of (32767.0/gfp->PeakSample)*(gfp->scale) but it's usually
           * very inaccurate. So we'd rather advice him to disable scaling first
           * and see our suggestion on the scale factor then.
           */
          System.out.print("using --scale <arg>\n"
              + "         (For   a   suggestion  on  the  optimal  value  of  <arg>  encode\n"
              + "         with  --scale 1  first)\n");
        }

      } else { /* no clipping */
        if (noclipGainChange > -0.1)
          System.out.print(
              "\nThe waveform does not clip and is less than 0.1dB away from full scale.\n");
        else
          System.out.printf(
              "\nThe waveform does not clip and is at least %.1fdB away from full scale.\n",
              -noclipGainChange);
      }
    }

  }

  private static int write_xing_frame(LameGlobalFlags gf,
      RandomAccessFile outf) {
    byte mp3buffer[] = new byte[Lame.LAME_MAXMP3BUFFER];

    int imp3 = VBRTag.getLameTagFrame(gf, mp3buffer);
    if (imp3 > mp3buffer.length) {
      System.err.printf(
          "Error writing LAME-tag frame: buffer too small: buffer BAG_SIZE=%d  frame BAG_SIZE=%d\n",
          mp3buffer.length, imp3);
      return -1;
    }
    if (imp3 <= 0) {
      return 0;
    }
    try {
      outf.write(mp3buffer, 0, imp3);
    } catch (@SuppressWarnings("unused") IOException e) {
      System.err.println("Error writing LAME-tag");
      return -1;
    }
    return imp3;
  }

  /**
   * 
   * @param gf
   * @param outf
   * @param nogap
   * @param inPath
   * @param outPath
   * 
   * @return zero on success or error code to use as process return code 
   */
  private static int lame_encoder(LameGlobalFlags gf, DataOutput outf,
      boolean nogap, String inPath, String outPath) {
    byte mp3buffer[] = new byte[Lame.LAME_MAXMP3BUFFER];
    int buffer[][] = new int[2][1152];

    encoder_progress_begin(gf, inPath, outPath);

    int imp3 = ID3Tag.lame_get_id3v2_tag(gf, mp3buffer);
    if (imp3 > mp3buffer.length) {
      encoder_progress_end(gf);
      System.err.printf(
          "Error writing ID3v2 tag: buffer too small: buffer BAG_SIZE=%d  ID3v2 BAG_SIZE=%d\n",
          mp3buffer.length, imp3);
      return 1;
    }
    try {
      // Write tag
      outf.write(mp3buffer, 0, imp3);
    } catch (@SuppressWarnings("unused") IOException e) {
      encoder_progress_end(gf);
      System.err.printf("Error writing ID3v2 tag \n");
      return 1;
    }

    int iread;
    /* encode until we hit eof */
    do {
      /* read in 'iread' samples */
      iread = gaud.get_audio(gf, buffer);

      if (iread >= 0) {
        encoder_progress(gf);

        /* encode */
        imp3 = lame.lame_encode_buffer_int(gf, buffer[0], buffer[1], iread,
            mp3buffer, 0, mp3buffer.length);

        /* was our output buffer big enough? */
        if (imp3 < 0) {
          if (imp3 == -1)
            System.err.printf("mp3 buffer is not big enough... \n");
          else
            System.err.printf("mp3 internal error:  error code=%d\n", imp3);
          return 1;
        }

        try {
          outf.write(mp3buffer, 0, imp3);
        } catch (@SuppressWarnings("unused") IOException e) {
          encoder_progress_end(gf);
          System.err.printf("Error writing mp3 output \n");
          return 1;
        }
      }
    } while (iread > 0);

    if (nogap)
      imp3 = lame.lame_encode_flush_nogap(gf, mp3buffer, mp3buffer.length);
    /*
     * may return one more mp3 frame
     */
    else
      imp3 = lame.lame_encode_flush(gf, mp3buffer, 0, mp3buffer.length);
    /*
     * may return one more mp3 frame
     */

    if (imp3 < 0) {
      if (imp3 == -1)
        System.err.printf("mp3 buffer is not big enough... \n");
      else
        System.err.printf("mp3 internal error:  error code=%d\n", imp3);
      return 1;

    }

    encoder_progress_end(gf);

    try {
      outf.write(mp3buffer, 0, imp3);
    } catch (@SuppressWarnings("unused") IOException e) {
      encoder_progress_end(gf);
      System.err.printf("Error writing mp3 output \n");
      return 1;
    }

    imp3 = ID3Tag.lame_get_id3v1_tag(gf, mp3buffer, mp3buffer.length);
    if (imp3 > mp3buffer.length) {
      System.err.printf(
          "Error writing ID3v1 tag: buffer too small: buffer BAG_SIZE=%d  ID3v1 BAG_SIZE=%d\n",
          mp3buffer.length, imp3);
    } else {
      if (imp3 > 0) {
        try {
          outf.write(mp3buffer, 0, imp3);
        } catch (@SuppressWarnings("unused") IOException e) {
          encoder_progress_end(gf);
          System.err.printf("Error writing ID3v1 tag \n");
          return 1;
        }
      }
    }

    if (parse.silent <= 0) {
      print_lame_tag_leading_info(gf);
    }
    try {
      ((Closeable) outf).close();
      try (RandomAccessFile rf = new RandomAccessFile(outPath, "rw")) {
        rf.seek(imp3);
        write_xing_frame(gf, rf);
      }
    } catch (@SuppressWarnings("unused") IOException e) {
      System.err.printf("fatal error: can't update LAME-tag frame!\n");
    }

    print_trailing_info(gf);
    return 0;
  }

  private static void parse_nogap_filenames(int nogapout, String inPath,
      StringBuilder outPath, StringBuilder outdir) {
    outPath.setLength(0);
    outPath.append(outdir);
    if (0 == nogapout) {
      outPath.setLength(0);
      outPath.append(inPath);
      /* nuke old extension, if one */
      if (outPath.toString().endsWith(".wav")) {
        outPath.setLength(0);
        outPath.append(outPath.substring(0, outPath.length() - 4) + ".mp3");
      } else {
        outPath.setLength(0);
        outPath.append(outPath + ".mp3");
      }
    } else {
      int slasher = inPath.lastIndexOf(System.getProperty("file.separator"));

      /* backseek to last dir delimiter */

      /* skip one forward if needed */
      if (slasher != 0
          && (outPath.toString().endsWith(System.getProperty("file.separator"))
              || outPath.toString().endsWith(":")))
        slasher++;
      else if (slasher == 0
          && (!outPath.toString().endsWith(System.getProperty("file.separator"))
              || outPath.toString().endsWith(":")))
        outPath.append(System.getProperty("file.separator"));

      outPath.append(inPath.substring(slasher));
      /* nuke old extension */
      if (outPath.toString().endsWith(".wav")) {
        String string = outPath.substring(0, outPath.length() - 4) + ".mp3";
        outPath.setLength(0);
        outPath.append(string);
      } else {
        String string = outPath + ".mp3";
        outPath.setLength(0);
        outPath.append(string);
      }
    }
  }

  private static final int MAX_NOGAP = 200;

  @SuppressWarnings("resource")
  public static void main(@NonNull String[] args) throws IOException {
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
    lame = new Lame(ga, bs, p, qupvt, qu, vbr, ver, id3, mpg);
    tak.setModules(qupvt);
    vbr.setModules(lame, bs, ver);
    parse = new Parse(ver, id3, p);
    gaud = new GetAudio(parse, mpg);

    StringBuilder outPath = new StringBuilder();
    StringBuilder nogapdir = new StringBuilder();
    StringBuilder inPath = new StringBuilder();

    /* support for "nogap" encoding of up to 200 .wav files */
    int nogapout = 0;
    int max_nogap = MAX_NOGAP;
    String[] nogap_inPath = new String[max_nogap];

    /* initialize libmp3lame */
    parse.input_format = sound_file_format.sf_unknown;
    LameGlobalFlags gf = Lame.lame_init();
    try {
      if (args.length < 1) {
        Parse.usage(System.err, "lame");
        /*
         * no command-line args, print usage, exit
         */
        System.exit(1);
      }

      /*
       * parse the command line arguments, setting various flags in the struct
       * 'gf'. If you want to parse your own arguments, or call libmp3lame from
       * a program which uses a GUI to set arguments, skip this call and set the
       * values of interest in the gf struct. (see the file API and lame.h for
       * documentation about these parameters)
       */
      /* Quick & very Dirty */
      String argv = System.getenv("LAMEOPT");
      if (argv != null && argv.length() > 0) {
        StringTokenizer tok = new StringTokenizer(argv, " ");
        ArrayList<String> args1 = new ArrayList<>();
        while (tok.hasMoreTokens())
          args1.add(tok.nextToken());
        parse.parse_args(gf, args1, inPath, outPath, null, null);
      }

      ArrayList<String> argsList = new ArrayList<>();
      for (String arg : args)
        argsList.add(arg);

      Parse.NoGap ng = new Parse.NoGap();
      int ret = parse.parse_args(gf, argsList, inPath, outPath, nogap_inPath, ng);
      if (ret < 0)
        System.exit(ret == -2 ? 0 : 1);

      max_nogap = ng.num_nogap;

      if (parse.update_interval < 0.)
        parse.update_interval = 2.f;

      if (outPath.length() != 0 && max_nogap > 0) {
        nogapdir = outPath;
        nogapout = 1;
      }

      /* add variables for encoder delay/padding */
      Enc enc = new Enc();
      DataOutputStream outf;
      /*
       * initialize input file. This also sets samplerate and as much other data
       * on the input file as available in the headers
       */
      if (max_nogap > 0) {
        /*
         * for nogap encoding of multiple input files, it is not possible to
         * specify the output file name, only an optional output directory.
         */
        parse_nogap_filenames(nogapout, nogap_inPath[0], outPath, nogapdir);
        outf = init_files(gf, nogap_inPath[0], outPath.toString(), enc);
      } else {
        outf = init_files(gf, inPath.toString(), outPath.toString(), enc);
      }

      /*
       * turn off automatic writing of ID3 tag data into mp3 stream we have to
       * call it before 'lame_init_params', because that function would spit out
       * ID3v2 tag data.
       */
      gf.write_id3tag_automatic = false;

      /*
       * Now that all the options are set, lame needs to analyze them and set
       * some more internal options and check for problems
       */
      int i = lame.lame_init_params(gf);
      if (i < 0) {
        if (i == -1) {
          Parse.display_bitrates(System.err);
        }
        System.err.println("fatal error during initialization");
        System.exit(i);
      }

      if (gf.decode_only) {
        /* decode an mp3 file to a .wav */
        if (parse.mp3_delay_set)
          lame_decoder(gf, outf, parse.mp3_delay, inPath.toString(),
              outPath.toString(), enc);
        else
          lame_decoder(gf, outf, 0, inPath.toString(), outPath.toString(), enc);

      } else {
        if (max_nogap > 0) {
          /*
           * encode multiple input files using nogap option
           */
          for (i = 0; i < max_nogap; ++i) {
            boolean use_flush_nogap = (i != (max_nogap - 1));
            if (i > 0) {
              parse_nogap_filenames(nogapout, nogap_inPath[i], outPath,
                  nogapdir);
              /*
               * note: if init_files changes anything, like samplerate,
               * num_channels, etc, we are screwed
               */
              outf = init_files(gf, nogap_inPath[i], outPath.toString(), enc);
              /*
               * reinitialize bitstream for next encoding. this is normally done
               * by lame_init_params(), but we cannot call that routine twice
               */
              lame.lame_init_bitstream(gf);
            }
            gf.internal_flags.nogap_total = max_nogap;
            gf.internal_flags.nogap_current = i;

            ret = lame_encoder(gf, outf, use_flush_nogap, nogap_inPath[i],
                outPath.toString());

            ((Closeable) outf).close();
            gaud.close_infile(); /* close the input file */

          }
        } else {
          ret = lame_encoder(gf, outf, false, inPath.toString(), outPath.toString());
          outf.close();
          gaud.close_infile(); /* close the input file */
          System.exit(ret);
        }
      }
    } finally {
      Lame.lame_close(gf);
    }
  }

  private static void encoder_progress_begin(LameGlobalFlags gf, String inPath,
      String outPath) {
    if (parse.silent < 10) {
      Lame.lame_print_config(gf);
      /* print useful information about options being used */

      System.out.printf("Encoding %s%s to %s\n", inPath,
          inPath.length() + outPath.length() < 66 ? "" : "\n     ", outPath);

      System.out.printf("Encoding as %g kHz ", 1.e-3 * gf.out_samplerate);

      {
        String[][] mode_names = {
            { "stereo", "j-stereo", "dual-ch", "single-ch" },
            { "stereo", "force-ms", "dual-ch", "single-ch" } };
        switch (gf.VBR) {
        case vbr_rh:
          System.out.printf("%s MPEG-%d%s Layer III VBR(q=%g) qval=%d\n",
              mode_names[gf.force_ms ? 1 : 0][gf.mode.ordinal()],
              2 - gf.version, gf.out_samplerate < 16000 ? ".5" : "",
              gf.VBR_q + gf.VBR_q_frac, gf.quality);
          break;
        case vbr_mt:
        case vbr_mtrh:
          System.out.printf("%s MPEG-%d%s Layer III VBR(q=%d)\n",
              mode_names[gf.force_ms ? 1 : 0][gf.mode.ordinal()],
              2 - gf.version, gf.out_samplerate < 16000 ? ".5" : "",
              gf.quality);
          break;
        case vbr_abr:
          System.out.printf(
              "%s MPEG-%d%s Layer III (%gx) average %d kbps qval=%d\n",
              mode_names[gf.force_ms ? 1 : 0][gf.mode.ordinal()],
              2 - gf.version, gf.out_samplerate < 16000 ? ".5" : "",
              0.1 * (int) (10. * gf.compression_ratio + 0.5),
              gf.VBR_mean_bitrate_kbps, gf.quality);
          break;
        default:
          System.out.printf("%s MPEG-%d%s Layer III (%gx) %3d kbps qval=%d\n",
              mode_names[gf.force_ms ? 1 : 0][gf.mode.ordinal()],
              2 - gf.version, gf.out_samplerate < 16000 ? ".5" : "",
              0.1 * (int) (10. * gf.compression_ratio + 0.5), gf.brate,
              gf.quality);
          break;
        }
      }

      if (parse.silent <= -10) {
        Lame.lame_print_internals(gf);
      }
      System.out.print("|");
      for (int i = 0; i < MAX_WIDTH - 2; i++) {
        System.out.print("=");
      }
      System.out.println("|");
      oldPercent = curPercent = oldConsoleX = 0;
    }
  }

  private static double last_time = 0.0;

  private static void encoder_progress(LameGlobalFlags gf) {
    if (parse.silent <= 0) {
      int frames = gf.frameNum;
      if (parse.update_interval <= 0) {
        /* most likely --disptime x not used */
        if ((frames % 100) != 0) {
          /* true, most of the time */
          return;
        }
      } else {
        if (frames != 0 && frames != 9) {
          double act = System.currentTimeMillis();
          double dif = act - last_time;
          if (dif >= 0 && dif < parse.update_interval) {
            return;
          }
        }
        last_time = System.currentTimeMillis();
        /* from now! disp_time seconds */
      }
      timestatus(gf.frameNum, lame_get_totalframes(gf));
    }
  }

  private static void encoder_progress_end(LameGlobalFlags gf) {
    if (parse.silent <= 0) {
      timestatus(gf.frameNum, lame_get_totalframes(gf));
      System.out.print("|");
      for (int i = 0; i < MAX_WIDTH - 2; i++) {
        System.out.print("=");
      }
      System.out.println("|");
    }
  }

  private static int oldPercent, curPercent, oldConsoleX;

  private static void timestatus(int frameNum, int totalframes) {
    int percent;

    if (frameNum < totalframes) {
      percent = (int) (100. * frameNum / totalframes + 0.5);
    } else {
      percent = 100;
    }
    boolean stepped = false;
    if (oldPercent != percent) {
      progressStep();
      stepped = true;
    }
    oldPercent = percent;
    if (percent == 100) {
      for (int i = curPercent; i < 100; i++) {
        progressStep();
        stepped = true;
      }
    }
    if (percent == 100 && stepped) {
      System.out.println();
    }
  }

  private final static int MAX_WIDTH = 79;

  private static void progressStep() {
    curPercent++;
    float consoleX = (float) curPercent * MAX_WIDTH / 100f;
    if ((int) consoleX != oldConsoleX)
      System.out.print(".");
    oldConsoleX = (int) consoleX;
    support.firePropertyChange("progress", oldPercent, curPercent);
  }

  /**
   * LAME's estimate of the total number of frames to be encoded. Only valid if
   * calling program set num_samples.
   */
  private static int lame_get_totalframes(LameGlobalFlags gfp) {
    /* estimate based on user set num_samples: */
    int totalframes = (int) (2 + ((double) gfp.num_samples * gfp.out_samplerate)
        / ((double) gfp.in_samplerate * gfp.framesize));

    return totalframes;
  }

  private static void WriteBytesSwapped(DataOutput fp, short[] p, int pPos)
      throws IOException {
    fp.writeShort(p[pPos]);
  }

  private static void writeBytes(DataOutput fp, short[] p, int pPos)
      throws IOException {
    /* No error condition checking */
    fp.write(p[pPos] & 0xff);
    fp.write(((p[pPos] & 0xffff) >> 8) & 0xff);
  }

}
