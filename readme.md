# EHE-SC

supercollider for earth horns electronics

a work in progress!

## installation

- install the `SuperCollider` application, which can be downloaded here: https://supercollider.github.io/

- copy the file `EHE.sc` to the SuperCollider extensions folder. there are actually two of these; one for the system/all users, one for only the current user. on macOS, the user's extensions directory is: `~/Library/Application Support/SuperCollider/Extensions`. (with `~` being the home directory, e.g. `/Users/ezra`.)

- the program expects to find the four earth horn recordings at a specific location: 
`~/Desktop/earth_horns/2021 recordings/Pipe horn 1.wav`
`~/Desktop/earth_horns/2021 recordings/Pipe horn 2.wav`
`~/Desktop/earth_horns/2021 recordings/Pipe horn 3.wav`
`~/Desktop/earth_horns/2021 recordings/Pipe horn 4.wav`

it will not work otherwise!

## running

after installation, the patch should run automatically the next time the `SuperCollider` application is run.

## usage

see the **guide**: [link](guide/index.md)

### morph time and crossfade

oscillator frequency changes now crossfade over the same duration as the preset morph time. set the morph time in the morph window (the "time" number box); the crossfade will match this value. by default this is 100 seconds.

### envelope timing modulation

each of the 4 envelope followers supports dynamic timing modulation on both rise and fall phases:

- in-rising (`mod_in_rise`): scales the rise time based on the rectified input amplitude. positive values lengthen rise when the input is loud; negative values shorten rise.
- in-falling (`mod_in_fall`): scales the fall time based on the rectified input amplitude. positive values lengthen fall; negative values shorten fall.
- out-rising (`mod_out_rise`): scales the rise time based on the envelope’s own output amplitude (feedback). positive values lengthen rise with larger envelopes; negative values shorten rise.
- out-falling (`mod_out_fall`): scales the fall time based on the envelope output amplitude. positive values lengthen fall; negative values shorten fall.

details:
- modulation is applied multiplicatively to the base `rise` / `fall` times and is clamped to a minimum duration
- the envelope `shape` blends between a slew-limited and lag-based response
- these modulation parameters are included in presets and participate in morphing

### DC offset to VCA CV

each oscillator has a dedicated **DC offset source** feeding its `vca_cv` control bus. this is implemented as a separate synth (`\ehe_dc`) per oscillator and is controlled in the modulation panel by the top row labeled "DC → osc N" (number-box and slider).

scaled envelope signals and DC offsets are summed together and rectified before each VCA level input. VCA -> VCA signals are *not* rectifed. thus, negative envelope weights and DC offsets do not by themselves produce inverted non-zero amplitudes.

## ROADMAP

in rough priority order:

- [x] configure server for multichannel input; device selection (config file?)
- [x] add controls for morph rate / duration
- [x] cross-fade frequency changes instead of glissando
- [x] add basic attack/release controls for envelope times
- [ ] add other synthesis parameters to presets: drift amounts, mod delay times
- [ ] "performance mode" UI: simplified controls, basic level / clip indicators, prettier
- [x] add controls to seek to different parts of the recorded input
- [x] add preset import / export
- [x] add controls to introduce live input
- [x] visualize / scope signals
- [x] morph presets
- [ ] control morph from MIDI

and in parallel, of course, make any needed changes / refinements to the sound!
