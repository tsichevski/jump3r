package ru.wowa.jump3r.mp3;

class VBRPresets {
	public VBRPresets(int qual, int comp, int compS,
			int y, float shThreshold, float shThresholdS,
			float adj, float adjShort, float lower,
			float curve, float sens, float inter,
			int joint, int mod, float fix) {
		vbr_q = qual;
		quant_comp = comp;
		quant_comp_s = compS;
		expY = y;
		st_lrm = shThreshold;
		st_s = shThresholdS;
		masking_adj = adj;
		masking_adj_short = adjShort;
		ath_lower = lower;
		ath_curve = curve;
		ath_sensitivity = sens;
		interch = inter;
		safejoint = joint;
		sfb21mod = mod;
		msfix = fix;
	}

	int vbr_q;
	int quant_comp;
	int quant_comp_s;
	int expY;
	/**
	 * short threshold
	 */
	float st_lrm;
	float st_s;
	float masking_adj;
	float masking_adj_short;
	float ath_lower;
	float ath_curve;
	float ath_sensitivity;
	float interch;
	int safejoint;
	int sfb21mod;
	float msfix;
}
