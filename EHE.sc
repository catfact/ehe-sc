EHE {

	classvar playback_dir = "~/Desktop/earth_horns/2021\ recordings/";

	classvar playback_paths;

	// starting position for playback, in seconds
	classvar file_start = 0;

	classvar hz_init_base = 48;

	classvar hz_init;

	//--------------

	classvar <ehe; // singleton instance

	var <s; // server

	var <b; // busses

	var <g;  // groups

	var <z; // synths

	var <d; // data

	var <buf;

	*initClass {

		playback_paths = [
			"Pipe horn 1.wav",
			"Pipe horn 2.wav",
			"Pipe horn 3.wav",
			"Pipe horn 4.wav",
		].collect({ arg filename; playback_dir.standardizePath ++ filename });

		hz_init = hz_init_base * Array.series(7, 1, 1);

		// randomize them a little :P
		7.do({ arg i; hz_init[i] = (hz_init[i].cpsmidi + 0.14.rand2).midicps });

		StartUp.add({
			var s = Server.default;

			s.waitForBoot {
				postln("EHE booted");
				ehe = EHE.new(s);
			}
		 });
	}

	*new { arg aServer;
		^super.new.init(aServer);
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

			this.add_osc;
			this.add_gui;

		}.play;
	}


	//-----------------------------------------------------------------
	// ---- create busses

	add_busses {

		b = Event.new;

		// input: 4x mono
		b[\src] = Array.fill(4, { Bus.audio(s, 1) });

		// envelopes: 4x mono
		b[\env] = Array.fill(4, { Bus.audio(s, 1) });
		// control-rate copies (for metering)
		b[\env_kr] = Array.fill(4, { Bus.control(s, 1) });

		// oscillators: 7x mono
		b[\osc] = Array.fill(7, { Bus.audio(s, 1) });

		// per-oscillator VCA control inputs...
		b[\vca_cv] = Array.fill(7, { Bus.audio(s, 1) });
		b[\vca_cv_amp] = Array.fill(7, { Bus.audio(s, 1) });

		// ... and modulated outputs...
		b[\vca_out] = Array.fill(7, { Bus.audio(s, 1) });
		// ... and amplitudes (for metering)
		b[\vca_out_amp] = Array.fill(7, { Bus.control(s, 1) });

		// final output: single stereo bus
		b[\mix] = Bus.audio(s, 2);
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

		// input
		z[\src] = Array.fill(4, { arg i;
			buf[i].postln;
			// { buf[i].inspect; }.defer;
			Synth.new(\ehe_playback, [
				\out, b[\src][i].index,
				\buf, buf[i].bufnum
			], target:g[\input])
		});

		// oscillators
		z[\osc] = Array.fill(7, { arg i;
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

		// VCA matrix
		// z[\vca] = Array.fill(7, { arg i;
		// 	Array.fill(4, { arg j;
		// 		Synth.new(\ehe_vca, [
		// 			\out, b[\osc_mod][i],
		// 			\in, b[\osc][i].index,
		// 			\mod, b[\env][j].index
		// 		], addAction:\addToTail);
		// 	})
		// });

		z[\vca] = Array.fill(7, { arg i;
			Synth.new(\ehe_vca, [
				\out, b[\vca_out][i],
				\in, b[\osc][i].index,
				\mod, b[\vca_cv][i].index
			], target:g[\vca]);
		});


		// patch cables from envelopes to VCA CV inputs
		z[\env_vca] = Array.fill(4, { arg i;
			Array.fill(7, { arg j;
				Synth.new(\ehe_patch, [
					\out, b[\vca_cv][j].index,
					\in, b[\env][i].index,
				], target:g[\env_vca]);
			});
		});

		// patch cables from oscillators to VCA CV inputs
		z[\vca_vca] = Array.fill(7, { arg i;
			Array.fill(7, { arg j;
				Synth.new(\ehe_patch_delay, [
					out: b[\vca_cv][j].index,
					in: b[\vca_out][i].index,
				], target:g[\vca_vca]);
			});
		});

		// output level/pan
		z[\mix] = Array.fill(7, { arg i;
			Synth.new(\ehe_mix, [
				\out, b[\mix].index,
				\in, b[\vca_out][i].index
			], target:g[\mix]);
		});

		// final output patch
		z[\out] = {
			var snd = In.ar(b[\mix].index, 2);
			snd = snd * Line.kr(dur:10);
			snd = snd.softclip;
			Out.ar(0, snd);
		}.play(s, addAction:\addToTail);

		// { b[\src][0].scope }.defer;
	}


	//-----------------------------------------------------------------
	// ---- initial settings
	init_params {

		4.do({ arg i;
			z[\env][i].set(\gain, 24.dbamp, \c, 0.dbamp);
		});

		z[\mix][0].set(\level, -6.dbamp, \pan, 0);
		z[\mix][1].set(\level, -6.dbamp, \pan, -0.2);
		z[\mix][2].set(\level, -6.dbamp, \pan, 0.2);
		z[\mix][3].set(\level, -6.dbamp, \pan, -0.4);
		z[\mix][4].set(\level, -6.dbamp, \pan, 0.4);
		z[\mix][5].set(\level, -10.dbamp, \pan, -0.8);
		z[\mix][6].set(\level, -10.dbamp, \pan, 0.8);

		1.wait;


		//--- patch levels

		// direct env to osc for the first 4 oscs
		z[\env_vca][0][0].set(\c, 1);
		z[\env_vca][1][1].set(\c, 1);
		z[\env_vca][2][2].set(\c, 1);
		z[\env_vca][3][3].set(\c, 1);

		// for last 3 oscs, 1x direct (scaled) feedvack connection,
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

	}



	//-----------------------------------------------------------------
	// ---- OSC bindings / responders
	add_osc {

		// internal envelope responders:
	}

	add_gui {
		// TODO
	}

}


//-----------------------------------------------------------------
// ---- synth definitions

EHE_defs {
	*send { arg s;

		// envelope follower node
		SynthDef.new(\ehe_env, {

			// x = rectified input
			var x = In.ar(\in.kr(0)); // * \g.kr(1);

			/*
		// this version is closer to serge patch (i think?)
		// has the drawback of having little viaration if threshold isn't dialed exactly

		// t = threshold
		// a = attack
		// r = release
		var trig = Amplitude.kr(x) > \t.kr(0.001);
		var times = [\a.kr(1), \r.kr(6)];
		var curves = [\sine, \sine];
		var env_spec = Env.new([0, 1, 0], times, curves, releaseNode:1);
		var env = EnvGen.ar(env_spec, trig);
		*/

			var ax = x.abs;
			var gated = (ax * \gain.kr(1.0)).min(1.0) * (ax > \t.kr(0.001));

			// simple exponential lag with separate rise/fall coefficients
			var env = LagUD.ar(gated, \a.kr(7.0), \r.kr(7.0));

			// c = multiplier
			// b = offset
			// y = output
			var y = ((env * \c.kr(1.0)) + \b.kr(0.0)).min(1.0);

			Out.ar(\out.kr(0), y);
			// env.poll;
		}).send(s);

		// oscillator node
		SynthDef.new(\ehe_osc, {
			// TODO: we'll work on making this substantially more interesting:
			// - harmonic content (waveshaping, etc) [added phase modulation feedback]
			// - drift? [added, but maybe not right yet]
			// - hiss/noise?

			var drift = LFNoise2.kr(\drift_rate.kr(0.01), \drift_st.kr(0.07));
			var fb_drift = LFNoise2.kr(\fb_drift_rate.kr(0.01));
			var feedback = \feedback.kr(3/7) * fb_drift.max(\fb_floor.kr(0.07));
			var osc = SinOscFB.ar(\hz.kr(48) * K2A.ar(drift.midiratio), feedback);
			Out.ar(\out.kr(0), osc * \amp.kr(0.1));
		}).send(s);

		// VCA node
		SynthDef.new(\ehe_vca, {
			var level = K2A.ar(\level.kr(1).lag(1));
			var mod = In.ar(\mod.kr(1));
			var gain = level * mod.softclip;
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
			c = c.min(1).max(-1);
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
			var lag = \lag.kr(0.1);
			a = a.min(1).max(0);
			c = c.min(1).max(-1);
			// a = Lag.ar(a, lag);
			// c = Lag.ar(c, lag);
			x = (x * c) + a;
			x = BufDelayL.ar(LocalBuf(s.sampleRate * 0.1), x, \delay.kr(0.09).min(0.099));
			Out.ar(\out.kr, x);
		}).send(s);

		// output mix / pan node
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
	}


}

//-----------------------------------------------------------------
// ---- GUI

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

		sl_pan = Slider(this, w@20);
		h = h - 20;
		num_pan = NumberBox(this, w@20);
		h = h - 20;

		sl_pan.action_({ arg sl;
			var val = sl.value.linlin(0, 1, -1, 1);
			num_pan.value = val;
			synth.set(\pos, val)
		});


		sl_level = Slider(this, w@(h-20));
		this.decorator.nextLine;
		num_level = NumberBox(this, w@20);

		sl_level.action_({ arg sl;
			var val = sl.value;
			if (val > 0.25, {
				val = val.linlin(0.25, 1.0, 0.25.ampdb, 12).dbamp;
			});
			synth.set(\level, val);
			num_level.value = val.ampdb;
		});
		sl_pan.value = 0.5;
	}

}

EHE_gui_mod_channel : View {

	// modulation levels from envelopes
	var sl_env;
	// modulation levels from VCAs
	var sl_vca;

	*new { arg parent, bounds, channel;
		^super.new(parent, bounds).init(parent, bounds, channel);
	}

	init { arg parent, bounds, channel;
		var w = bounds.width;
		var h = bounds.height;

		this.decorator = FlowLayout(bounds, 0@0, 0@0);

		sl_env = Array.fill(4, { arg i;
			Slider(this, w@20).action_({ arg sl;
				var val = sl.value.linlin(0, 1, -2, 2);
				EHE.ehe.z[\env_vca][i][channel].set(\c, val);
			})
		});

		sl_vca = Array.fill(7, { arg i;
			Slider(this, w@20).action_({ arg sl;
				var val = sl.value.linlin(0, 1, -2, 2);
				EHE.ehe.z[\vca_vca][i][channel].set(\c, val);
			})
		});

	}
}

EHE_gui {
	var <e;
	var <w;

	var <mix_channels;
	var <mod_channels;

	*new { ^super.new.init;}

	init {
		e = EHE.ehe;

		w = Window.new("EHE", Rect(100, 100, 800, 400));
		w.front;
		w.view.decorator = FlowLayout(w.view.bounds, 8@8, 8@8);

		mix_channels = Array.fill(7, { arg i;
			EHE_gui_mix_channel(w, Rect(0, 0, 80, 240), e.z[\mix][i]);
		});
		w.view.decorator.nextLine;


		mod_channels = Array.fill(7, { arg i;
			EHE_gui_mod_channel(w, Rect(0, 0, 80, 240), i);
		});

	}
}
