package ru.wowa.jump3r.mp3;

/**
 * encode a frame with a desired average bitrate
 * 
 * mt 2000/05/31
 * 
 * @author Ken
 * 
 */
public class ABRIterationLoop implements IIterationLoop {

	/**
	 * 
	 */
	private Quantize quantize;

	/**
	 * @param quantize
	 */
	ABRIterationLoop(Quantize quantize) {
		this.quantize = quantize;
	}

	@Override
  public void iteration_loop(LameGlobalFlags gfp,
			float pe[][], float ms_ener_ratio[],
			III_psy_ratio ratio[][]) {
		LameInternalFlags gfc = gfp.internal_flags;
		assert gfc != null;
		float l3_xmin[] = new float[L3Side.SFBMAX];
		float xrpow[] = new float[576];
		int targ_bits[][] = new int[2][2];
		int max_frame_bits[] = new int[1];
		int analog_silence_bits[] = new int[1];
		IIISideInfo l3_side = gfc.l3_side;

		int mean_bits = 0;

		Quantize.calc_target_bits(gfp, pe, ms_ener_ratio, targ_bits,
				analog_silence_bits, max_frame_bits);

		/*
		 * encode granules
		 */
		for (int gr = 0; gr < gfc.mode_gr; gr++) {

			if (gfc.mode_ext == Encoder.MPG_MD_MS_LR) {
				Quantize.ms_convert(gfc.l3_side, gr);
			}
			for (int ch = 0; ch < gfc.channels_out; ch++) {
				float adjust, masking_lower_db;
				GrInfo cod_info = l3_side.tt[gr][ch];

				if (cod_info.block_type != Encoder.SHORT_TYPE) {
					// NORM, START or STOP type
					adjust = 0;
					masking_lower_db = gfc.PSY.mask_adjust - adjust;
				} else {
					adjust = 0;
					masking_lower_db = gfc.PSY.mask_adjust_short - adjust;
				}
				gfc.masking_lower = (float) Math.pow(10.0,
						masking_lower_db * 0.1);

				/*
				 * cod_info, scalefac and xrpow get initialized in
				 * init_outer_loop
				 */
				this.quantize.init_outer_loop(gfc, cod_info);
				if (Quantize.init_xrpow(gfc, cod_info, xrpow)) {
					/*
					 * xr contains energy we will have to encode calculate the
					 * masking abilities find some good quantization in
					 * outer_loop
					 */
					@SuppressWarnings("null")
          int ath_over = QuantizePVT.calc_xmin(gfp,	ratio[gr][ch], cod_info, l3_xmin);
					if (0 == ath_over) /* analog silence */
						targ_bits[gr][ch] = analog_silence_bits[0];

					this.quantize.outer_loop(gfp, cod_info, l3_xmin, xrpow, ch,
							targ_bits[gr][ch]);
				}
				this.quantize.iteration_finish_one(gfc, gr, ch);
			} /* ch */
		} /* gr */

		/*
		 * find a bitrate which can refill the resevoir to positive BAG_SIZE.
		 */
		for (gfc.bitrate_index = gfc.VBR_min_bitrate; gfc.bitrate_index <= gfc.VBR_max_bitrate; gfc.bitrate_index++) {

			MeanBits mb = new MeanBits(mean_bits);
			int rc = Reservoir.ResvFrameBegin(gfp, mb);
			mean_bits = mb.bits;
			if (rc >= 0)
				break;
		}
		assert (gfc.bitrate_index <= gfc.VBR_max_bitrate);

		Reservoir.ResvFrameEnd(gfc, mean_bits);
	}
}
