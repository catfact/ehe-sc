~dir = PathName(Document.current.path).pathOnly;
~rec_dir = PathName(~dir ++ "../2021\ recordings/").pathOnly;

~file_paths= [
	"Pipe horn 1.wav",
	"Pipe horn 2.wav",
	"Pipe horn 3.wav",
	"Pipe horn 4.wav",
].collect({ arg filename; ~rec_dir ++ filename });

// initial oscillator frequencies
~hz_init = 48 * Array.series(7, 1, 1);

// randomize them a little :P
7.do({ arg i; ~hz_init[i] = (~hz_init[i].cpsmidi + 0.14.rand2).midicps });
~hz_init.postln;

 ~start = 60 * 2.6;
// ~start = 0;

Routine {
	s = Server.default;
	s.boot;
	s.sync;

	//-----------------------------------------------------------------
	// ---- synth definitions

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
		// - FM matrix?

		var drift = LFNoise2.kr(\drift_rate.kr(0.01), \drift_st.kr(0.07));
		var fb_drift = LFNoise2.kr(\fb_drift_rate.kr(0.01));
		var feedback = \feedback.kr(3/7) * fb_drift.max(\fb_floor.kr(0.07));
		var osc = SinOscFB.ar(\hz.kr(48) * K2A.ar(drift.midiratio), feedback);
		Out.ar(\out.kr(0), osc * \amp.kr(0.1));
	}).send(s);

	// VCA node
	SynthDef.new(\ehe_vca, {
		var level = K2A.ar(\level.kr(0).lag(1));
		var mod = In.ar(\mod.kr(1));
		var gain = level * mod;
		Out.ar(\out.kr(0), In.ar(\in.kr(0)) * gain);
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
		// Amplitude.kr(snd).poll;
		Out.ar(\out.kr(0), snd);
	}).send(s);

	s.sync;

	// ----------------------------------------------------------------
	// ---- buffers

	~buf = ~file_paths.collect({ arg path;
		var sf = SoundFile.new;
		var startFrame;

		sf.openRead(path);
		postln("cueing soundfile: " );
		path.postln;
		sf.postln;
		postln("channels: " ++ sf.numChannels);
		postln("frames: " ++ sf.numFrames);
		postln("samplerate: " ++ sf.sampleRate);
		startFrame = ~start * sf.sampleRate;
		sf.close;

		Buffer.cueSoundFile(s, path,
			startFrame: startFrame,
			numChannels:1, bufferSize: 262144);
	});

	s.sync;

	// ----------------------------------------------------------------
	// ---- busses

	b = Event.new;

	// input: 4x mono
	b[\src] = Array.fill(4, { Bus.audio(s, 1) });
	// 4.do({ arg i; { b[\src][i].scope }.defer; });

	// envelopes: 4x mono
	// (could be made control-rate)
	b[\env] = Array.fill(4, { Bus.audio(s, 1) });

	// oscillators: 7x mono
	b[\osc] = Array.fill(7, { Bus.audio(s, 1) });

	// modulated and summed oscillators
	b[\osc_mod] = Array.fill(7, { Bus.audio(s, 1) });

	// output: single stereo bus
	b[\mix] = Bus.audio(s, 2);

	// ----------------------------------------------------------------
	// ---- synths
	z = Event.new;

	// input synths
	z[\src] = Array.fill(4, { arg i;
		// { ~buf[i].plot }.defer;
		Synth.new(\ehe_playback, [
			\out, b[\src][i].index,
			\buf, ~buf[i].bufnum
		], addAction:\addToHead);
	});

	// oscillator synths
	z[\osc] = Array.fill(7, { arg i;
		Synth.new(\ehe_osc, [
			\out, b[\osc][i].index,
			\hz, ~hz_init[i]
		], addAction:\addToTail);
	});

	// envelope follower synths
	z[\env] = Array.fill(4, { arg i; Synth.new(\ehe_env, [
		\out, b[\env][i].index,
		\in, b[\src][i].index
	], addAction:\addToTail);
	});

	// VCA matrix synths
	z[\vca] = Array.fill(7, { arg i;
		Array.fill(4, { arg j;
			Synth.new(\ehe_vca, [
				\out, b[\osc_mod][i],
				\in, b[\osc][i].index,
				\mod, b[\env][j].index
			], addAction:\addToTail);
		})
	});

	// output level/pan
	z[\mix] = Array.fill(7, { arg i;
		Synth.new(\ehe_mix, [
			\out, b[\mix].index,
			\in, b[\osc_mod][i].index
		], addAction:\addToTail)
	});

	// final output patch
	z[\out] = {
		Out.ar(0, In.ar(b[\mix].index, 2));
	}.play(s, addAction:\addToTail);

	//-------------------
	//---- initial settings

	4.do({ arg i;
		z[\env][i].set(\gain, 24.dbamp, \c, 0.dbamp);
	});

	z[\mix][0].set(\level, -6.dbamp, \pan, 0);
	z[\mix][1].set(\level, -6.dbamp, \pan, -0.2);
	z[\mix][2].set(\level, -6.dbamp, \pan, 0.2);
	z[\mix][3].set(\level, -6.dbamp, \pan, -0.4);
	z[\mix][4].set(\level, -6.dbamp, \pan, 0.4);
	z[\mix][5].set(\level, -6.dbamp, \pan, -0.8);
	z[\mix][6].set(\level, -6.dbamp, \pan, 0.8);

	1.wait;

	4.do({ arg i;
		// FIXME: better / more efficient to have one VCA per osc,
		// and a separate layer of connections from envs to vcas
		z[\vca][i][i].set(\level, 1);
	});

	z[\vca][4][0].set(\level, 0.5);
	z[\vca][4][1].set(\level, 0.5);

	z[\vca][5][1].set(\level, 0.5);
	z[\vca][5][2].set(\level, 0.5);

	z[\vca][6][2].set(\level, 0.5);
	z[\vca][6][3].set(\level, 0.5);


	///////////////
	/// visualization / control

	/// TODO..

	// simplest display of busses
	~bscope = Bus.new('audio', b[\src][0].index, 8, s);
	{ ~bscope.scope; }.defer;
}.play;