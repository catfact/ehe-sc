////////////////////////////////////////////////////////////////////////
// ---- main synth class!

EHE {

	classvar <numOscs = 8;

	classvar <shouldAddToStartup = true;

	classvar <playback_dir = "~/Desktop/earth_horns/2021 recordings/";

	classvar <playback_paths;

	classvar <preset_dir = "~/Desktop/earth_horns/ehe-presets";

	// classvar <file_start = 360;
	classvar <file_start = 0;

	classvar <hz_init_base = 48;
	classvar <hz_init;

	//--------------

	classvar <ehe; // singleton instance
	classvar <gui;
	classvar <mph; // morph controller
	classvar <mph_gui;

	var <s; // server
	var <b; // busses
	var <g;  // groups
	var <z; // synths
	//	var <d; // data
	var <buf; // streaming buffer for disk input

	//var <bscope; // wrapper busses for scoping

	*initClass {

		playback_paths = [
			"Pipe horn 1.wav",
			"Pipe horn 2.wav",
			"Pipe horn 3.wav",
			"Pipe horn 4.wav",
		].collect({ arg filename; playback_dir.standardizePath ++ filename });

		hz_init = hz_init_base * Array.series(EHE.numOscs, 1, 1);

		// randomize them a little :P
		//		EHE.numOscs.do({ arg i; hz_init[i] = (hz_init[i].cpsmidi + 0.14.rand2).midicps });

		postln("EHE initClass");
		postln("EHE shouldAddToStartup = " ++ shouldAddToStartup);

		if (shouldAddToStartup, {
			StartUp.add({
				EHE.startup;
			});
		});
	}

	*new { arg aServer;
		^super.new.init(aServer);
	}


	*startup {
		var s = Server.default;
		var opt = ServerOptions.new;
		opt.numInputBusChannels_(4);
		s.options_(opt);

		s.waitForBoot {
			postln("EHE booted");
			Routine {
				ehe = EHE.new(s);
				s.sync;
				1.wait;
				{
					gui = EHE_gui.new;
					mph = EHE_state_morph.new;
					mph.init;
					mph_gui = EHE_morph_gui.new;
				}.defer;

			}.play;
		}
	}

	//------------------------------------------------------
	/// private

	init { arg aServer;
		s =  aServer;

		Routine {
			EHE_defs.send(s);
			s.sync;

			buf = playback_paths.collect({ arg path;
				var sf = SoundFile.new;
				var startFrame;

				sf.openRead(path);
				postln("cueing soundfile: " );
				path.postln;
				sf.postln;
				postln("channels: " ++ sf.numChannels);
				postln("frames: " ++ sf.numFrames);
				postln("samplerate: " ++ sf.sampleRate);
				startFrame = file_start * sf.sampleRate;
				postln("startFrame: " ++ startFrame);
				sf.close;

				Buffer.cueSoundFile(s, path,
					startFrame: startFrame,
					numChannels:1,
					bufferSize: 262144);
			});
			s.sync;

			// { buf.do({ arg bf; bf.plot }) }.defer;


			this.add_busses;
			this.add_nodes;
			s.sync;

			this.init_params;

			// this.add_osc;
			// this.add_gui;

		}.play;
	}


	//-----------------------------------------------------------------
	// ---- create busses

	add_busses {

		b = Event.new;

		// oscillators: Nx mono
		b[\osc] = Array.fill(EHE.numOscs, { Bus.audio(s, 1) });

		// input: 4x mono
		b[\src] = Array.fill(4, { Bus.audio(s, 1) });

		// envelopes: 4x mono
		b[\env] = Array.fill(4, { Bus.audio(s, 1) });

		// per-oscillator VCA control inputs...
		b[\vca_cv] = Array.fill(EHE.numOscs, { Bus.audio(s, 1) });

		// ...and modulated outputs
		b[\vca_out] = Array.fill(EHE.numOscs, { Bus.audio(s, 1) });

		// final output: single stereo bus
		b[\mix] = Bus.audio(s, 2);

		// control rate busses for metering
		b[\env_kr] = Array.fill(4, { Bus.control(s, 1) });
		b[\vca_cv_amp] = Array.fill(EHE.numOscs, { Bus.control(s, 1) });
		b[\vca_out_amp] = Array.fill(EHE.numOscs, { Bus.control(s, 1) });
	}


	//-----------------------------------------------------------------
	// ---- create synth and group nods

	add_nodes {

		// --- groups
		g = Event.new;

		// sources and processors
		g[\input] = Group.new;
		g[\env] = Group.after(g[\input]);
		g[\osc] = Group.after(g[\env]);
		g[\vca] = Group.after(g[\osc]);

		// patches
		g[\env_vca] = Group.before(g[\vca]);
		g[\vca_vca] = Group.before(g[\vca]);
		g[\mix] = Group.after(g[\vca]);

		// k-rate monitoring
		g[\kr] = Group.tail(s);

		// --- synths
		z = Event.new;

		//-- input

		// playback sources
		z[\src] = Array.fill(4, { arg i;
			buf[i].postln;
			// { buf[i].inspect; }.defer;
			Synth.new(\ehe_playback, [
				\out, b[\src][i].index,
				\buf, buf[i].bufnum
			], target:g[\input])
		});

		// live input sources
		z[\adc] = Array.fill(4, { arg i;
			Synth.new(\ehe_adc, [
				\out, b[\src][i].index,
				\in, i
			], target:g[\input]);
		});

		// oscillators
		z[\osc] = Array.fill(EHE.numOscs, { arg i;
			Synth.new(\ehe_osc, [
				\out, b[\osc][i].index,
				\hz, hz_init[i]
			], target:g[\osc]);
		});

		// envelope followers
		z[\env] = Array.fill(4, { arg i; Synth.new(\ehe_env, [
			\out, b[\env][i].index,
			\in, b[\src][i].index
		], target:g[\env]);
		});

		z[\vca] = Array.fill(EHE.numOscs, { arg i;
			Synth.new(\ehe_vca, [
				\out, b[\vca_out][i],
				\in, b[\osc][i].index,
				\mod, b[\vca_cv][i].index
			], target:g[\vca]);
		});


		// patch cables from envelopes to VCA CV inputs
		z[\env_vca] = Array.fill(4, { arg i;
			Array.fill(EHE.numOscs, { arg j;
				Synth.new(\ehe_patch, [
					\out, b[\vca_cv][j].index,
					\in, b[\env][i].index,
				], target:g[\env_vca]);
			});
		});

		// patch cables from oscillators to VCA CV inputs
		z[\vca_vca] = Array.fill(EHE.numOscs, { arg i;
			Array.fill(EHE.numOscs, { arg j;
				Synth.new(\ehe_patch_delay, [
					out: b[\vca_cv][j].index,
					in: b[\vca_out][i].index,
				], target:g[\vca_vca]);
			});
		});

		// output level/pan
		z[\mix] = Array.fill(EHE.numOscs, { arg i;
			Synth.new(\ehe_mix, [
				\out, b[\mix].index,
				\in, b[\vca_out][i].index
			], target:g[\mix]);
		});

		// final output patch (synth)
		z[\out] = {
			var snd = In.ar(b[\mix].index, 2);
			snd = snd * Line.kr(dur:10);
			snd = snd * \level.kr(0).lag(1);
			snd = snd.softclip;
			Out.ar(0, snd);
		}.play(s, addAction:\addToTail);

		// source monitor synths
		g[\monitor] = Group.tail(s);
		z[\monitor] = Array.fill(4, {  arg i;
			Synth.new(\ehe_mix, [
				\in, b[\src][i].index,
				\out, 0,
				\level, 0,
				\pos, i.linlin(0, 3, -0.6, 0.6),
			], g[\monitor])
		});


		// { b[\src][0].scope }.defer;
	}

	//-----------------------------------------------------------------
	// ---- initial settings
	init_params {

		4.do({ arg i;
			z[\env][i].set(\gain, 24.dbamp, \c, 0.dbamp);
		});

		z[\mix][0].set(\level, -6.dbamp,  \pos, 0);
		z[\mix][1].set(\level, -6.dbamp,  \pos, -0.2);
		z[\mix][2].set(\level, -6.dbamp,  \pos, 0.2);
		z[\mix][3].set(\level, -6.dbamp,  \pos, -0.4);
		z[\mix][4].set(\level, -6.dbamp,  \pos, 0.4);
		z[\mix][5].set(\level, -10.dbamp, \pos, -0.8);
		z[\mix][6].set(\level, -10.dbamp, \pos, 0.8);
		z[\mix][7].set(\level, -12.dbamp, \pos, 0);

		1.wait;


		//--- patch levels

		// direct env to osc for the first 4 oscs
		z[\env_vca][0][0].set(\c, 1);
		z[\env_vca][1][1].set(\c, 1);
		z[\env_vca][2][2].set(\c, 1);
		z[\env_vca][3][3].set(\c, 1);

		// for last 3 oscs, 1x direct (scaled) feedback connection,
		// and 3x inverting env->osc connections

		// e = EHE.ehe;
		z[\vca_vca][0][4].set(\c, 0.85);
		z[\env_vca][1][4].set(\c, -0.125);
		z[\env_vca][2][4].set(\c, -0.125);
		z[\env_vca][3][4].set(\c, -0.125);

		z[\env_vca][0][5].set(\c, -0.125);
		z[\vca_vca][1][5].set(\c, 0.85);
		z[\env_vca][2][5].set(\c, -0.125);
		z[\env_vca][3][5].set(\c, -0.125);

		z[\env_vca][0][6].set(\c, -0.125);
		z[\env_vca][1][6].set(\c, -0.125);
		z[\vca_vca][2][6].set(\c, 0.85);
		z[\env_vca][3][6].set(\c, -0.125);

		z[\env_vca][0][7].set(\c, -0.125);
		z[\env_vca][1][7].set(\c, -0.125);
		z[\vca_vca][2][7].set(\c, -0.125);
		z[\env_vca][3][7].set(\c, 0.85);

	}

	seek_playback {
		arg sec;
		4.do({ arg i;
			buf[i].cueSoundFile(EHE.playback_paths[i], sec * s.sampleRate);
		});
	}

	// change an oscillator frequency using a crossfade
	crossfade_osc_freq { arg i, hz;
		z[\osc][i].get(\hz, { arg old_hz;
			if (hz != old_hz, {
				postln(
					"crossfading osc " ++ (i+1) ++
					" from " ++ old_hz ++ " Hz to " ++ hz ++ " Hz"
				);
				z[\osc][i].set(\gate, 0);
				z[\osc][i] = Synth.new(\ehe_osc, [
					\out, b[\osc][i].index,
					\hz, hz
				], target:g[\osc]);
				{ gui.update_osc_freq(i, hz); }.defer;
			}, {
				postln("skipping freq change for osc " ++ (i+1) ++ ", same value: " ++ hz);
			});
		});

	}

}


//-----------------------------------------------------------------
// ---- synth definitions

EHE_defs {
	*send { arg s;

		// envelope follower node
		SynthDef.new(\ehe_env, {

			var y = LocalIn.ar(1);

			// x = rectified input
			var x = In.ar(\in.kr(0)); // * \g.kr(1);

			var ax = x.abs;
			var gated = (ax * \gain.kr(1.0)).min(1.0) * (ax > \t.kr(0.001));

			// simple exponential lag with separate rise/fall coefficients
			var riseTime = \rise.kr(12);
			var fallTime = \fall.kr(24);

			// modulation weights
			var modInRise = \mod_in_rise.kr(0);
			var modInFall = \mod_in_fall.kr(0);
			var modOutRise = \mod_out_rise.kr(0);
			var modOutFall = \mod_out_fall.kr(0);

			var riseTimeMod = (riseTime * (1 + (x*modInRise) + (y.abs*modOutRise))).max(0.001);
			var fallTimeMod = (fallTime * (1 + (x*modInFall) + (y.abs*modOutFall))).max(0.001);

			// fudge factor to make exp/lin times more perceptually similar
			var lagTimeMult = 1.4;
			var riseSlope = (riseTimeMod * lagTimeMult).reciprocal.min(999);
			var fallSlope = (fallTimeMod * lagTimeMult).reciprocal.min(999);
			var lag = LagUD.ar(gated, riseTimeMod, fallTimeMod);
			var slew = Slew.ar(lag, riseSlope, fallSlope);
			var env = SelectX.ar(\shape.kr(0), [slew, lag]);

			LocalOut.ar(env);

			Out.ar(\out.kr(0), env);
			// env.poll;
		}).send(s);

		// oscillator node
		SynthDef.new(\ehe_osc, {
			var aenv = EnvGen.kr(Env.asr(1, 1, 1), \gate.kr(1), timeScale: \fade_time.kr(21), doneAction:2);
			var drift = LFNoise2.kr(\drift_rate.kr(0.01), \drift_st.kr(0.07));
			var fb_drift = LFNoise2.kr(\fb_drift_rate.kr(0.01));
			var feedback = \feedback.kr(1/7) * fb_drift.max(\fb_floor.kr(0.02));
			var osc = SinOscFB.ar(\hz.kr(48) * K2A.ar(drift.midiratio), feedback);
			Out.ar(\out.kr(0), osc * \amp.kr(0.1) * aenv);
		}).send(s);

		// VCA node
		SynthDef.new(\ehe_vca, {
			var level = K2A.ar(\level.kr(1).lag(1));
			var mod = In.ar(\mod.kr(1));
			//var gain = level * mod.softclip;
			var gain = level * mod;
			Out.ar(\out.kr(0), In.ar(\in.kr(0)) * gain);
		}).send(s);

		// inverting / attenuating patch cable
		SynthDef.new(\ehe_patch,{
			var c = \c.kr;
			var x = In.ar(\in.kr);
			// scaled offset when inverting
			var a = (c < 0) * (c * -1);
			var lag = \lag.kr(0.1);
			a = a.min(1).max(0);
			c = c.min(4).max(-4);
			a = Lag.kr(a, lag);
			c = Lag.kr(c, lag);
			x = (x * c) + a;
			Out.ar(\out.kr, x);
		}).send(s);

		// inverting / attenuating / delaying patch cable (for feedback)
		SynthDef.new(\ehe_patch_delay,{
			var c = \c.kr;
			var x = InFeedback.ar(\in.kr);
			// scaled offset when inverting
			var a = (c < 0) * (c * -1);
			var lag = \lag.kr(4.0);
			a = a.min(1).max(0);
			c = c.min(1).max(-1);
			a = Lag.kr(a, lag);
			c = Lag.kr(c, lag);
			x = (x * c) + a;
			x = BufDelayL.ar(LocalBuf(s.sampleRate * 0.1), x, \delay.kr(0.09).min(0.099));
			Out.ar(\out.kr, x);
		}).send(s);

		// output mix / pan node (mono -> stereo)
		SynthDef.new(\ehe_mix, {
			var level = Lag.kr(\level.kr(0), \level_lag.kr(1));
			var pos = Lag.kr(\pos.kr(0), \pos_lag.kr(1));
			Out.ar(\out.kr(0), Pan2.ar(In.ar(\in.kr(0)), pos, level))
		}).send(s);

		// buffer playback node
		SynthDef.new(\ehe_playback, {
			var buf = \buf.kr;
			var snd = DiskIn.ar(1, buf, loop:\loop.kr(1));
			// Amplitude.kr(snd).ampdb.poll;
			Out.ar(\out.kr(0), snd * \level.kr(1));
		}).send(s);

		// utility: downsample AR bus to KR
		SynthDef.new(\ehe_a2k, {
			Out.kr(\out.kr, A2K.kr(In.ar(\in.kr)));
		}).send(s);

		// utility: basic amplitude follower for metering
		SynthDef.new(\ehe_amp, {
			Out.kr(\out.kr, Amplitude.kr(In.ar(\in.kr)));
		});

		// live input
		SynthDef.new(\ehe_adc, {
			var snd = SoundIn.ar(\in.kr(0));
			Out.ar(\out.kr(0), snd * \level.kr(1));
		}).send(s);

	}

}

//-----------------------------------------------------------------
// ---- GUI

EHE_gui_color {
	// TODO
	*env { arg idx;
	}

	*vca { arg idx;
	}
}

EHE_gui_mix_channel : View {
	// sliders
	var <sl_level, <sl_pan;
	// numeric displays
	var <num_level, <num_pan;

	*new { arg parent, bounds, synth;
		^super.new(parent, bounds).init(parent, bounds, synth);
	}

	init { arg parent, bounds, synth;
		var w = bounds.width;
		var h = bounds.height;

		this.decorator = FlowLayout(bounds, 0@0, 0@0);

		sl_pan = Slider(this, w@20).thumbSize_(3);
		h = h - 20;
		num_pan = NumberBox(this, w@20);
		h = h - 20;

		sl_pan.action_({ arg sl;
			var val = sl.value.linlin(0, 1, -1, 1);
			num_pan.valueAction_(val);
		});
		num_pan.action_({ arg num;
			var val = num.value;
			synth.set(\pos, val);
			sl_pan.value = val.linlin(-1, 1, 0, 1);
		});

		sl_level = Slider(this, w@(h-20)).thumbSize_(3);
		this.decorator.nextLine;
		num_level = NumberBox(this, w@20);

		sl_level.action_({ arg sl;
			var val = sl.value;
			if (val > 0.25, {
				val = val.linlin(0.25, 1.0, 0.25.ampdb, 12).dbamp;
			});
			num_level.valueAction_(val.ampdb);
		});
		num_level.action_({ arg num;
			var val = num.value.dbamp;
			synth.set(\level, val);
		});

	}

}

EHE_gui_mod_channel : View {
	// modulation levels from envelopes
	var <sl_env;
	var <num_env;
	// modulation levels from VCAs
	var <sl_vca;
	var <num_vca;

	classvar <slSpec;

	*initClass {
		slSpec = ControlSpec.new(-4, 4);
	}

	*new { arg parent, bounds, channel;
		^super.new(parent, bounds).init(parent, bounds, channel);
	}

	init { arg parent, bounds, channel;
		var w = bounds.width;
		var h = bounds.height;

		this.decorator = FlowLayout(bounds, 0@0, 0@0);
		sl_env = Array.newClear(4);
		num_env = Array.newClear(4);
		sl_vca = Array.newClear(EHE.numOscs);
		num_vca = Array.newClear(EHE.numOscs);

		4.do({ arg i;
			sl_env[i] = Slider(this, w@20).thumbSize_(3);
			num_env[i] = NumberBox(this, w@20);
			sl_env[i].action_({ arg sl;
				//var val = sl.value.linlin(0, 1, -2, 2);
				var val = slSpec.map(sl.value);
				num_env[i].valueAction_(val);
			}).value_(0.5);
			num_env[i].action_({ arg num;
				var val = num.value;
				EHE.ehe.z[\env_vca][i][channel].set(\c, val);
				sl_env[i].value = slSpec.unmap(val);
			})
		});

		EHE.numOscs.do({ arg i;
			sl_vca[i] = Slider(this, w@20).thumbSize_(3);
			num_vca[i] = NumberBox(this, w@20);
			sl_vca[i].action_({ arg sl;
				//var val = sl.value.linlin(0, 1, -2, 2);
				var val = slSpec.map(sl.value);
				num_vca[i].valueAction_(val);
			});
			num_vca[i].action_({ arg num;
				var val = num.value;
				EHE.ehe.z[\vca_vca][i][channel].set(\c, val);
				sl_vca[i].value = slSpec.unmap(val);
			});
		});

	}
}


// "main" / global section of the GUIß
EHE_gui_main : View {
	var <sl_level;
	var <num_level;

	var <but_adc;
	var <but_src;
	var <but_seek;
	var <num_seek;

	*new { arg parent, bounds;
		^super.new(parent, bounds).init(parent, bounds);
	}
	init { arg parent, bounds;
		var w = bounds.width;
		var h = bounds.height;

		this.decorator = FlowLayout(bounds, 0@0, 0@0);

		but_adc = Button(this, w@20);
		but_adc.states_([
			["input off", Color.white, Color.grey],
			["input on", Color.black, Color.white]
		]);
		but_adc.action_({ arg but;
			var val = but.value;
			var level = if(val == 1, { 1 }, { 0 });
			EHE.ehe.z[\adc].do({ arg s;
				s.set(\level, level);
			});
		});
		this.decorator.nextLine;
		but_src = Button(this, w@20);
		but_src.states_([
			["playback off", Color.white, Color.grey],
			["playback on", Color.black, Color.white]
		]);
		but_src.action_({ arg but;
			var val = but.value;
			var level = if(val == 1, { 1 }, { 0 });
			EHE.ehe.z[\src].do({ arg s;
				s.set(\level, level);
			});
		});


		but_adc.value = 1;
		but_src.value = 1;

		h = h - 40;
		this.decorator.nextLine;

		num_seek = NumberBox(this, w@20);
		this.decorator.nextLine;
		but_seek = Button(this, w@20);
		but_seek.states_([
			["seek", Color.black, Color.white]
		]);
		but_seek.action_({ arg but;
			var sec = num_seek.value;
			EHE.ehe.seek_playback(sec);
		});
		h = h - 40;


		StaticText(this, 60@20).string_("attack:");
		NumberBox(this, w-60@20).action_({ arg num;
			var val = num.value;
			EHE.ehe.g[\env].set(\a, val);
		});
		this.decorator.nextLine;

		StaticText(this, 60@20).string_("release: ");
		NumberBox(this, w-60@20).action_({ arg num;
			var val = num.value;
			EHE.ehe.g[\env].set(\r, val);
		});
		this.decorator.nextLine;
		h = h - 40;

		sl_level = Slider(this, w@(h-20));
		this.decorator.nextLine;
		num_level = NumberBox(this, w@20);

		sl_level.action_({ arg sl;
			var val = sl.value;
			if (val > 0.25, {
				val = val.linlin(0.25, 1.0, 0.25.ampdb, 12).dbamp;
			});
			num_level.valueAction_(val.ampdb);
		});
		num_level.action_({ arg num;
			var val = num.value.dbamp;
			EHE.ehe.z[\out].set(\level, val);
		});
	}
}

EHE_gui_env_mod : View {

	var <sl_shape;
	var <num_shape;
	var <sl_mod_in_rise;
	var <num_mod_in_rise;
	var <sl_mod_in_fall;
	var <num_mod_in_fall;
	var <sl_mod_out_rise;
	var <num_mod_out_rise;
	var <sl_mod_out_fall;
	var <num_mod_out_fall;

	*new { arg parent, bounds;
		^super.new(parent, bounds).init(parent, bounds);
	}
	init { arg parent, bounds;
		var w = bounds.width / 3;
		var h = bounds.height;
		var num_w = w * 0.5;
		var label_w = w * 1.5;

		this.decorator = FlowLayout(bounds, 0@0, 0@0);

		sl_shape = Array.newClear(4);
		num_shape = Array.newClear(4);
		sl_mod_in_fall = Array.newClear(4);
		num_mod_in_fall = Array.newClear(4);
		sl_mod_in_rise = Array.newClear(4);
		num_mod_in_rise = Array.newClear(4);
		sl_mod_out_fall = Array.newClear(4);
		num_mod_out_fall = Array.newClear(4);
		sl_mod_out_rise = Array.newClear(4);
		num_mod_out_rise = Array.newClear(4);



		4.do({ arg i;
			StaticText(this, label_w@20).string_("env " ++ (i+1) ++ " shape:");
			sl_shape[i] = Slider(this, w@20).thumbSize_(3);
			num_shape[i] = NumberBox(this, num_w@20);
			sl_shape[i].action_({ arg sl;
				var val = sl.value.linlin(0, 1, 0, 1);
				num_shape[i].valueAction_(val);
			});
			num_shape[i].action_({ arg num;
				var val = num.value;
				EHE.ehe.z[\env][i].set(\shape, val);
				sl_shape[i].value = val.linlin(0, 1, 0, 1);
			});
			this.decorator.nextLine;

			StaticText(this, label_w@20).string_("env " ++ (i+1) ++ " mod in rise:");
			sl_mod_in_rise[i] = Slider(this, w@20).thumbSize_(3);
			num_mod_in_rise[i] = NumberBox(this, num_w@20);
			sl_mod_in_rise[i].action_({ arg sl;
				var val = sl.value.linlin(0, 1, -4, 4);
				num_mod_in_rise[i].valueAction_(val);
			});
			num_mod_in_rise[i].action_({ arg num;
				var val = num.value;
				EHE.ehe.z[\env][i].set(\mod_in_rise, val);
				sl_mod_in_rise[i].value = val.linlin(-4, 4, 0, 1);
			});
			this.decorator.nextLine;

			StaticText(this, label_w@20).string_("env " ++ (i+1) ++ " mod in fall:");
			sl_mod_in_fall[i] = Slider(this, w@20).thumbSize_(3);
			num_mod_in_fall[i] = NumberBox(this, num_w@20);
			sl_mod_in_fall[i].action_({ arg sl;
				var val = sl.value.linlin(0, 1, -4, 4);
				num_mod_in_fall[i].valueAction_(val);
			});
			num_mod_in_fall[i].action_({ arg num;
				var val = num.value;
				EHE.ehe.z[\env][i].set(\mod_in_fall, val);
				sl_mod_in_fall[i].value = val.linlin(-4, 4, 0, 1);
			});
			this.decorator.nextLine;

			StaticText(this, label_w@20).string_("env " ++ (i+1) ++ " mod out rise:");
			sl_mod_out_rise[i] = Slider(this, w@20).thumbSize_(3);
			num_mod_out_rise[i] = NumberBox(this, num_w@20);
			sl_mod_out_rise[i].action_({ arg sl;
				var val = sl.value.linlin(0, 1, -4, 4);
				num_mod_out_rise[i].valueAction_(val);
			});
			num_mod_out_rise[i].action_({ arg num;
				var val = num.value;
				EHE.ehe.z[\env][i].set(\mod_out_rise, val);
				sl_mod_out_rise[i].value = val.linlin(-4, 4, 0, 1);
			});
			this.decorator.nextLine;

			StaticText(this, label_w@20).string_("env " ++ (i+1) ++ " mod out fall:");
			sl_mod_out_fall[i] = Slider(this, w@20).thumbSize_(3);
			num_mod_out_fall[i] = NumberBox(this, num_w@20);
			sl_mod_out_fall[i].action_({ arg sl;
				var val = sl.value.linlin(0, 1, -4, 4);
				num_mod_out_fall[i].valueAction_(val);
			});
			num_mod_out_fall[i].action_({ arg num;
				var val = num.value;
				EHE.ehe.z[\env][i].set(\mod_out_fall, val);
				sl_mod_out_fall[i].value = val.linlin(-4, 4, 0, 1);
			});
			this.decorator.nextLine;


		})


	}
}

// top-level editor GUI
EHE_gui {
	var <e;
	var <w;
	var <wscope;

	var <labels; 		// labels container view
	var <ui; 			// ui widgets container view
	var <mainview; 		// main / global controls container view
	var <env_mod_view;  // envelope modulation controls container view

	var <mix_channels;
	var <mod_channels;
	var <tuning_nums;
	var <main;

	*new { ^super.new.init; }

	init {
		e = EHE.ehe;

		w = Window.new("EHE", Rect(100, 100, 1300, 800));
		w.front;
		w.view.decorator = FlowLayout(w.view.bounds, 0@0, 20@0);

		labels = View.new(w, 120@800);
		labels.decorator = FlowLayout(w.view.bounds, 0@0, 0@0);
		this.add_labels(labels);

		ui = View.new(w, 700@800);
		ui.decorator = FlowLayout(w.view.bounds, 0@0, 0@0);

		mainview = View.new(w, 120@800);
		//w.view.decorator
		env_mod_view = EHE_gui_env_mod.new(w, Rect(0, 0, 300, 800));

		tuning_nums = Array.fill(EHE.numOscs, { arg i;
			NumberBox(ui, 80@20).action_({ arg numbox;
				[numbox, numbox.value].postln;
				EHE.ehe.z[\osc][i].set(\hz, numbox.value);
			});
		});
		ui.decorator.nextLine;

		mix_channels = Array.fill(EHE.numOscs, { arg i;
			EHE_gui_mix_channel(ui, Rect(0, 0, 80, 240), e.z[\mix][i]);
		});
		ui.decorator.nextLine;

		mod_channels = Array.fill(EHE.numOscs, { arg i;
			EHE_gui_mod_channel(ui, Rect(0, 0, 80, 500), i);
		});

		main = EHE_gui_main.new(mainview, Rect(0, 0, 120, 740));

		//---  scope windows
		wscope = Event.new;
		[\src, \env, \vca_cv, \vca_out].do({ arg k, i;
			var nchan = if((k == \src) || (k == \env), { 4 }, { EHE.numOscs });
			{
				wscope[k] = Stethoscope.new(index: e.b[k][0].index, numChannels:nchan);
				wscope[k].window.name_(k.asString);
				wscope[k].window.bounds_(Rect(150 + (i*40), 150 + (i*40), 600, 400));
			}.defer;
		});

		^this
	}

	add_labels { arg v;
		var h = 20;
		var w = v.bounds.width;
		var h2 = h * 2;
		StaticText(v, w@h).string_("frequency Hz");
		v.decorator.nextLine;
		StaticText(v, w@h).string_("pan position");
		v.decorator.nextLine;
		StaticText(v, w@h).string_("");
		v.decorator.nextLine;

		StaticText(v, w@h).string_("osc level");
		v.decorator.nextLine;

		//////// spacer
		StaticText(v, w@180).string_("");
		v.decorator.nextLine;
		/////////

		StaticText(v, w@h2).string_("env 1 -> osc N");
		v.decorator.nextLine;
		StaticText(v, w@h2).string_("env 2 -> osc N");
		v.decorator.nextLine;
		StaticText(v, w@h2).string_("env 3 -> osc N");
		v.decorator.nextLine;
		StaticText(v, w@h2).string_("env 4 -> osc N");
		v.decorator.nextLine;
		StaticText(v, w@h2).string_("osc 1 -> osc N");
		v.decorator.nextLine;
		StaticText(v, w@h2).string_("osc 2 -> osc N");
		v.decorator.nextLine;
		StaticText(v, w@h2).string_("osc 3 -> osc N");
		v.decorator.nextLine;
		StaticText(v, w@h2).string_("osc 4 -> osc N");
		v.decorator.nextLine;
		StaticText(v, w@h2).string_("osc 5 -> osc N");
		v.decorator.nextLine;
		StaticText(v, w@h2).string_("osc 6 -> osc N");
		v.decorator.nextLine;
		StaticText(v, w@h2).string_("osc 7 -> osc N");
		v.decorator.nextLine;
		StaticText(v, w@h2).string_("osc 8 -> osc N");
		v.decorator.nextLine;
	}


	// refresh displayed values (e.g., if changed programmatically)
	// don't send updates to synths
	update_osc_freq { arg osc_idx, hz;
		tuning_nums[osc_idx].value = hz;
	}

	update_osc_level { arg osc_idx, level;
		var db = level.ampdb;
		var mindb = 0.25.ampdb;
		if (db <= mindb, {
			mix_channels[osc_idx].sl_level.value = level;
		}, {
			mix_channels[osc_idx].sl_level.value = db.linlin(mindb, 12, 0.25, 1);
		});
		mix_channels[osc_idx].num_level.value = db;
	}

	update_osc_pan { arg osc_idx, pos;
		mix_channels[osc_idx].sl_pan.value = pos.linlin(-1, 1, 0, 1);
		mix_channels[osc_idx].num_pan.value = pos;
	}

	update_mod_env { arg osc_idx, env_idx, val;
		mod_channels[osc_idx].sl_env[env_idx].value = EHE_gui_mod_channel.slSpec.unmap(val);
		mod_channels[osc_idx].num_env[env_idx].value = val;
	}
	update_mod_vca { arg osc_idx, vca_idx, val;
		mod_channels[osc_idx].sl_vca[vca_idx].value = EHE_gui_mod_channel.slSpec.unmap(val);
		mod_channels[osc_idx].num_vca[vca_idx].value = val;
	}


}

////////////////////////////////////////////////////////////////////////
// parameter state representation and I/O
EHE_state {

	// a state is a flat associative collection
	*new_state {
		var state = Dictionary.new;


		var noscs = EHE.numOscs;
		//postln("num oscs: "  ++ noscs);
		noscs.do({ arg i;
			var k;
			i.postln;

			k = ("freq_"++(i+1)).asSymbol;
			state[k] = EHE.hz_init[i];
			k = ("level_"++(i+1)).asSymbol;
			state[k] = 0;
			k = ("pan_"++(i+1)).asSymbol;
			state[k] = 0;

			4.do({ arg j;
				k = ("mod_env_"++(j+1)++"_"++(i+1)).asSymbol;
				state[k] = 0;
			});

			EHE.numOscs.do({ arg j;
				k = ("mod_vca_"++(j+1)++"_"++(i+1)).asSymbol;
				state[k] = 0;
			});
		});

		^state
	}

	*print_state { arg state;
		var ks = state.keys.asArray.sort;
		ks.do({ arg k;
			//[k, state[k]].postln;
			postln("\\" ++ k.asString ++ ", " ++ state[k] ++ ", ");
		});
	}

	*apply_state { arg x, e; //, gui;
		var noscs = EHE.numOscs;

		noscs.do({ arg i;
			var k;

			// done separately to allow crossfade
			/// FIXME: logic / API needs cleanup since introducing the xfade mechanism

			// k = ("freq_"++(i+1)).asSymbol;
			// if (x.includesKey(k), {
			// 	// e.z[\osc][i].set(\hz, x[k]);
			// 	EHE.ehe.crossfade_osc_freq(i, x[k]);
			// });
			//gui.update_osc_freq(i, x[k]);

			k = ("level_"++(i+1)).asSymbol;
			if (x.includesKey(k), {
				e.z[\mix][i].set(\level, x[k]);
			});
			//gui.update_osc_level(i, x[k]);

			k = ("pan_"++(i+1)).asSymbol;
			if (x.includesKey(k), {
				e.z[\mix][i].set(\pos, x[k]);
			});
			//gui.update_osc_pan(i, x[k]);

			4.do({ arg j;
				k = ("mod_env_"++(j+1)++"_"++(i+1)).asSymbol;
				if (x.includesKey(k), {
					e.z[\env_vca][j][i].set(\c, x[k]);
				});
				//gui.update_mod_env(i, j, x[k]);
			});

			EHE.numOscs.do({ arg j;
				k = ("mod_vca_"++(j+1)++"_"++(i+1)).asSymbol;
				if (x.includesKey(k), {
					e.z[\vca_vca][j][i].set(\c, x[k]);
				});
				// gui.update_mod_vca(i, j, x[k]);
			});
		});
	}

	*new_state_from_gui {
		arg gui;
		var state = Dictionary.new;
		var noscs = EHE.numOscs;
		noscs.do({ arg i;
			var k;

			k = ("freq_"++(i+1)).asSymbol;
			state[k] = gui.tuning_nums[i].value;

			k = ("level_"++(i+1)).asSymbol;
			state[k] = gui.mix_channels[i].sl_level.value;
			k = ("pan_"++(i+1)).asSymbol;
			state[k] = gui.mix_channels[i].sl_pan.value.linlin(0, 1, -1, 1);

			4.do({ arg j;
				k = ("mod_env_"++(j+1)++"_"++(i+1)).asSymbol;
				state[k] = gui.mod_channels[i].num_env[j].value;
			});

			EHE.numOscs.do({ arg j;
				k = ("mod_vca_"++(j+1)++"_"++(i+1)).asSymbol;
				state[k] = gui.mod_channels[i].num_vca[j].value;
			});
		});
		^state
	}

	// asynchronously populate state structure from running synth params
	*new_state_from_synth { arg e, callback;

		var state = Dictionary.new;
		var noscs = EHE.numOscs;
		Routine {
			noscs.do({ arg i;
				var k;
				var c = Condition.new;

				k = ("freq_"++(i+1)).asSymbol;
				e.z[\osc][i].get(\hz, { arg val;
					state[k] = val;
					c.unhang;
					// postln("unhung freq " ++ i);
				});
				c.hang;

				k = ("level_"++(i+1)).asSymbol;
				e.z[\mix][i].get(\level, { arg val;
					state[k] = val;
					c.unhang;
					// postln("unhung level " ++ i);
				});
				c.hang;

				k = ("pan_"++(i+1)).asSymbol;
				e.z[\mix][i].get(\pos, { arg val;
					state[k] = val;
					c.unhang;
					// postln("unhung pan " ++ i);
				});
				c.hang;

				4.do({ arg j;
					k = ("mod_env_"++(j+1)++"_"++(i+1)).asSymbol;
					e.z[\env_vca][j][i].get(\c, { arg val;
						state[k] = val;
						c.unhang;
						// postln("unhung mod_env_vca " ++ j ++ " " ++ i);
					});
					c.hang;
				});

				EHE.numOscs.do({ arg j;
					k = ("mod_vca_"++(j+1)++"_"++(i+1)).asSymbol;
					e.z[\vca_vca][j][i].get(\c, { arg val;
						state[k] = val;
						c.unhang;
						// postln("unhung mod_vca_vca " ++ j ++ " " ++ i);
					});
					c.hang;
				});
			});
			postln("all synth params retrieved; running callback");
			callback.value(state);
		}.play;
		^nil
	}

	*write_state_to_file { arg state, path;
		var file = File.new(path, "w");
		var ks = state.keys.asArray.sort;
		ks.do({ arg k;
			var str = "\\" ++ k.asString ++ ", " ++ state[k].asString ++ ", \n";
			str.post;
			file.write(str);
		});
		file.close;
	}

	*read_state_from_file { arg path;
		var state = Dictionary.new;
		var file = File.new(path, "r");
		var str = "[ " ++ file.readAllString ++ " ]";
		file.close;
		state = Dictionary.newFrom(str.interpret);
		^state
	}

	*refresh_gui_from_state { arg gui, state;
		{
			var noscs = EHE.numOscs;
			noscs.do({ arg i;
				var k;

				k = ("freq_"++(i+1)).asSymbol;
				if (state.includesKey(k), {
					gui.update_osc_freq(i, state[k]);
				});

				k = ("level_"++(i+1)).asSymbol;
				if (state.includesKey(k), {
					gui.update_osc_level(i, state[k]);
				});

				k = ("pan_"++(i+1)).asSymbol;
				if (state.includesKey(k), {
					gui.update_osc_pan(i, state[k]);
				});
				4.do({ arg j;
					k = ("mod_env_"++(j+1)++"_"++(i+1)).asSymbol;
					if (state.includesKey(k), {
						gui.update_mod_env(i, j, state[k]);
					});
				});

				EHE.numOscs.do({ arg j;
					k = ("mod_vca_"++(j+1)++"_"++(i+1)).asSymbol;
					if (state.includesKey(k), {
						gui.update_mod_vca(i, j, state[k]);
					});
				});
			});
		}.defer;

	}

}


/////////////////////////////////////////////////////////////////////////
// morph between parameter states

EHE_state_morph {
	var <>target;
	var <>previous;
	var <>current;

	var <>t = 0.0;
	var <>dt = 0.1;
	var <>r = 0.01;

	var <>isMorphing = false;

	var rout;

	*new_morphed_state { arg state_a, state_b, t;
		var state = Dictionary.new;
		var ks = state_a.keys.asArray;
		ks.do({ arg k;
			if (k.asString.contains("freq_"), {
				// skip frequencies; they are handled separately via crossfading
			}, {
				// linear interpolation for everything else
				var va = state_a[k];
				var vb = state_b[k];
				state[k] = va + ((vb - va) * t);
			});
		});
		^state
	}

	init {
		EHE_state.new_state_from_synth(EHE.ehe, { arg s;
			previous = s;
			current = previous;
			EHE_state.refresh_gui_from_state(EHE.gui, s);
			rout = Routine {
				loop {
					if (isMorphing, {
						// post("morphing t: " ++ t);
						{ EHE.mph_gui.status.string_("morphing: " ++ (t*100).round ++ "%"); }.defer;
						t = (t + (r*dt)).min(1.0);
						// postln(" -> " ++ t);
						current = EHE_state_morph.new_morphed_state(previous, target, t);

						// EHE_state.print_state(current);

						if (t >= 1.0, {
							isMorphing = false;
							previous = current;
							t = 0.0;
							{ EHE.mph_gui.status.string_("done morphing"); }.defer;
						});
						EHE_state.apply_state(current, EHE.ehe);
						EHE_state.refresh_gui_from_state(EHE.gui, current);
					});
					dt.wait;
				}
			}.play;
		});
	}

	morph_to { arg aTarget, rate=0.01;
		if (isMorphing, {
			previous = current;
			target = aTarget;
			t = 0;
		}, {
			// not morphing - get the current state from the synth
			EHE_state.new_state_from_synth(EHE.ehe, { arg state;
				//EHE_state.write_state_to_file(state, (psetdir ++ "/ehe_" ++ Date.new.stamp ++ ".scd").standardizePath);
				previous = state;
				current = state;
				target = aTarget;
				t = 0;
				isMorphing = true;
			});
		});
	}

	morph_to_file { arg path;
		var state = EHE_state.read_state_from_file(path);
		this.morph_to(state);
		// apply frequencies immediately via crossfade
		EHE.numOscs.do({ arg i;
			var k = ("freq_"++(i+1)).asSymbol;
			if (state.includesKey(k), {
				EHE.ehe.crossfade_osc_freq(i, state[k]);
			});
		});
	}
}

//////////////////////////
/// morph controller ui

EHE_morph_gui {
	var <w; // window

	// miscellaneous buttons
	var butSave;
	var butScan;
	var butCancel;

	var <paths; // known paths to preset files

	var <butsView;   // button container
	var <buts;       // individual button widgets

	var <status; // status display string

	var <numTime;

	*new { ^super.new.init }

	init {
		w = Window.new("morph", Rect(0, 0, 400, 600));
		w.view.decorator = FlowLayout(w.bounds, 10@10, 10@10);
		w.front;
		//		w.view.decorator = FlowLayout.new(w.view.bounds, 0@0, 0@0);

		butSave = Button.new(w, 60@40)
		.states_([
			["save", Color.black, Color.white]
		])
		.action_({ arg but;
			EHE_morph_gui.quicksave;
			this.scandir;
		});

		butScan = Button.new(w, 60@40)
		.states_([
			["scan", Color.black, Color.white]
		])
		.action_({ arg but;
			this.scandir;
		});


		butCancel= Button.new(w, 60@40)
		.states_([
			["cancel", Color.black, Color.white]
		])
		.action_({ arg but;
			EHE.mph.isMorphing = false;
			{ EHE.mph_gui.status.string_("morph cancelled"); }.defer;
		});

		w.view.decorator.nextLine;

		status = StaticText.new(w, 200@40);
		w.view.decorator.nextLine;

		StaticText.new(w, 60@40).string_("time: ");
		numTime = NumberBox(w, 60@40).action_({
			arg num;
			var time = 1 / num.value;
			var str = "setting morph time: " ++ time ++ " s";
			str.postln;
			EHE.mph.r = time;
		});


		w.view.decorator.nextLine;
		butsView = View(w, 300@600);
		butsView.decorator = FlowLayout.new(butsView.bounds, 0@0, 0@0);

		this.scandir;
	}

	// create a new preset file in the preset directory
	*quicksave {
		var psetdir = EHE.preset_dir;
		EHE_state.new_state_from_synth(EHE.ehe, { arg state;
			EHE_state.write_state_to_file(state, (psetdir ++ "/ehe_" ++ Date.new.stamp ++ ".scd").standardizePath);
		});

	}

	// scan the presets directory and update the list of load-preset buttons
	scandir {
		buts.do({ arg but; but.remove; });
		//if (butsView.notNil, { butsView.remove; });
		butsView.decorator.reset;

		buts = List.new;
		PathName(EHE.preset_dir.standardizePath).files.do({
			arg pathname;
			var path = pathname.fullPath;
			var filename = pathname.fileNameWithoutExtension;
			pathname.postln;
			buts.add(Button.new(butsView, 300@40)
				.states_([[filename, Color.black, Color.white]])
				.action_({
					EHE.mph.morph_to_file(path);
				})
			);
		});
	}
}