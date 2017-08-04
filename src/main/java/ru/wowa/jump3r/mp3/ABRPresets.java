package ru.wowa.jump3r.mp3;

class ABRPresets {
	public ABRPresets(@SuppressWarnings("unused") int kbps, int comp, int compS,
			int joint, float fix, float shThreshold,
			float shThresholdS, float bass, float sc,
			float mask, float lower, float curve,
			float interCh, int sfScale) {
		quant_comp = comp;
		quant_comp_s = compS;
		safejoint = joint;
		nsmsfix = fix;
		st_lrm = shThreshold;
		st_s = shThresholdS;
		nsbass = bass;
		scale = sc;
		masking_adj = mask;
		ath_lower = lower;
		ath_curve = curve;
		interch = interCh;
		sfscale = sfScale;
	}

	int quant_comp;
	int quant_comp_s;
	int safejoint;
	float nsmsfix;
	/**
	 * short threshold
	 */
	float st_lrm;
	float st_s;
	float nsbass;
	float scale;
	float masking_adj;
	float ath_lower;
	float ath_curve;
	float interch;
	int sfscale;
}
