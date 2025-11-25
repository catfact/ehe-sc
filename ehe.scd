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

Routine {
	s = Server.default;
	s.boot;
	s.sync;


	//-----------------------------------------------------------------
	// ---- synth definitions

	// envelope follower node
	SynthDef.new(\ehe_env, {
		// x = input
		// g = gain
		var x = In.ar(\in.kr(0)) * \g.kr(1);
		// t = threshold
		// a = attack
		// r = release
		var trig = Amplitude.kr(x) > \t.kr(-20.dbamp);
		var times = [\a.kr(1), \r.kr(6)];
		var curves = [\sine, \sine];
		var env = EnvGen.ar(Env.new([0, 1, 0], times, curves), trig);
		// c = multiplier
		// b = offset
		// y = output
		var y = (env * \c.kr(1)) + \b.kr(0);
		Out.ar(\out.kr(0), y);
	}).send(s);

	// oscillator node
	SynthDef.new(\ehe_osc, {
		// TODO: we'll work on making this substantially more interesting
		Out.ar(\out.kr(0), SinOsc.ar(\hz.kr(48)) * \amp.kr(0.1));
	}).send(s);

	// VCA node
	SynthDef.new(\ehe_vca, {
		Out.ar(\out.kr(0), In.ar(\sig.kr(0)) * In.ar(\mod.kr(1)));
	}).send(s);

	// output mix / pan node
	SynthDef.new(\ehe_mix, {
		var level = Lag.kr(\level.kr(0), \levelLag.kr(1));
		var pos = Lag.kr(\pos.kr(0), \posLag.kr(1));
		Out.ar(\out.kr(0), Pan2.ar(In.ar(\in.kr(0)), pos, level))
	}).send(s);

	// buffer playback node
	SynthDef.new(\ehe_playback, {
		// var buf = \buf.kr;
		// var snd = PlayBuf.ar(1, buf, \rate.kr(1).lag(1), loop:\loop.kr(1));
		var buf = \buf.kr;
		var snd = VDiskIn(1, buf, \rate.kr(1), loop:\loop.kr(1));
		Out.ar(\out.kr(0), snd);
	}).send(s);

	s.sync;

	// ----------------------------------------------------------------
	// ---- buffers

	// postln("reading buffers...");
	// ~buf = ~file_paths.collect({ arg path; Buffer.read(s, path); });
	// s.sync;
	// postln("done reading buffers");
	~buf = ~file_paths.collect({ arg path;
		Buffer.cueSoundFile(s, path, numChannels:1);
	});

	// ----------------------------------------------------------------
	// ---- busses

	b = Event.new;


	// input: 4x mono
	b[\src] = Array.fill(4, { Bus.audio(s, 1) });

	// envelopes: 4x mono
	// (could be made control-rate)
	b[\env] = Array.fill(4, { Bus.audio(s, 1) });

	// oscillators: 7x mono
	b[\osc] = Array.fill(7, { Bus.audio(s, 1) });

	// modulated and summed oscillators
	b[\osc_mod] = Array.fill(7, { Bus.audio(s, 1) });

	// output: single stereo bus
	b[\out] = Bus.audio(s, 2);

	// ----------------------------------------------------------------
	// ---- synths
	z = Event.new;

	// input synths
	z[\src] = Array.fill(4, { arg i;
		Synth.new(\ehe_playback, [
			\out, b[\src][i],
			\buf, ~buf[i].bufnum
		], addAction:\addToTail);

	});

	// oscillator synths
	z[\osc] = Array.fill(7, { arg i;
		Synth.new(\ehe_osc, [
			\out, b[\osc][i],
			\hz, ~hz_init[i]
		], addAction:\addToTail);
	});

	// envelope follower synths
	z[\env] = Array.fill(4, { arg i; Synth.new(\ehe_env, [
		\out, b[\env][i],
		\in, b[\osc][i]
	], addAction:\addToTail);
	});

	// VCA matrix synths
	z[\vca] = Array.fill(7, { arg i;
		Array.fill(4, { arg j;
			Synth.new(\ehe_vca, [
				\out, b[\osc_mod][i],
				\sig, b[\osc][i],
				\mod, b[\env][j]
			], addAction:\addToTail);
		})
	});

	// output level/pan

	z[\mix] = Array.fill(7, { arg i;
		Synth.new(\ehe_mix, [
			\out, b[\out],
			\in, b[\osc_mod][i]
		], addAction:\addToTail)
	});

}.play;