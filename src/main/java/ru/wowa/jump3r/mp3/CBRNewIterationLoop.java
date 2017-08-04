package ru.wowa.jump3r.mp3;

/**
 * author/date??
 * 
 * encodes one frame of MP3 data with constant bitrate
 * 
 * @author Ken
 * 
 */
public class CBRNewIterationLoop implements IIterationLoop {
	/**
	 * 
	 */
	private Quantize quantize;

	/**
	 * @param quantize
	 */
	CBRNewIterationLoop(Quantize quantize) {
		this.quantize = quantize;
	}

	@Override
  public void iteration_loop(LameGlobalFlags gfp, float pe[][],
			float ms_ener_ratio[], III_psy_ratio ratio[][]) {
		LameInternalFlags gfc = gfp.internal_flags;
		float l3_xmin[] = new float[L3Side.SFBMAX];
		float xrpow[] = new float[576];
		int targ_bits[] = new int[2];
		int mean_bits = 0, max_bits;
		IIISideInfo l3_side = gfc.l3_side;

		MeanBits mb = new MeanBits(mean_bits);
		Reservoir.ResvFrameBegin(gfp, mb);
		mean_bits = mb.bits;

		/* quantize! */
		for (int gr = 0; gr < gfc.mode_gr; gr++) {

			/*
			 * calculate needed bits
			 */
			max_bits = QuantizePVT.on_pe(gfp, pe, targ_bits, mean_bits,
					gr, gr);

			if (gfc.mode_ext == Encoder.MPG_MD_MS_LR) {
				Quantize.ms_convert(gfc.l3_side, gr);
				QuantizePVT.reduce_side(targ_bits, ms_ener_ratio[gr],
						mean_bits, max_bits);
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
				 * init_outer_loop sets up cod_info, scalefac and xrpow
				 */
				this.quantize.init_outer_loop(gfc, cod_info);
				if (Quantize.init_xrpow(gfc, cod_info, xrpow)) {
					/*
					 * xr contains energy we will have to encode calculate the
					 * masking abilities find some good quantization in
					 * outer_loop
					 */
					QuantizePVT.calc_xmin(gfp, ratio[gr][ch], cod_info,
							l3_xmin);
					this.quantize.outer_loop(gfp, cod_info, l3_xmin, xrpow, ch,
							targ_bits[ch]);
				}

				this.quantize.iteration_finish_one(gfc, gr, ch);
				assert (cod_info.part2_3_length <= LameInternalFlags.MAX_BITS_PER_CHANNEL);
				assert (cod_info.part2_3_length <= targ_bits[ch]);
			} /* for ch */
		} /* for gr */

		Reservoir.ResvFrameEnd(gfc, mean_bits);
	}
}
