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
- modulation is applied multiplicatively to the base `rise` / `fall` times and is clamped to a minimum duration to keep behavior stable.
- the envelope `shape` blends between a slew-limited and lag-based response.
- these modulation parameters are included in presets and participate in morphing, so changes will interpolate across morphs.

### vca offset

each oscillator's VCA includes an **offset** parameter that adds a constant DC bias to the VCA gain before applying the modulation. this allows:

- **softening** of the modulation response by raising the baseline gain (especially effective with negative or inverting modulators)
- **centering** of the VCA output in the presence of asymmetric modulation sources
- **pre-emphasis** of quieter oscillators or those receiving weak modulation signals

offset values range from -1 to +1; zero is the neutral point with no DC bias.

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
