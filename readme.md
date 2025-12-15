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

## user interface

the interface is very janky and minimal right now! so:
- there are no labels on UI elements!
- initial state of UI elements does not represent the initial state of the patch
- there is only minimal visual feedback (scope of envelope and oscillator busses)

the UI is arranged in 7 columns, one for each oscillator. in each column starting from the top, controlling oscillator `OSC_N`, the UI elements are:

- 1. number box to enter frequencies directly in Hz.
- 2. pan slider: -1 to 1 for L <-> R.
- 3. pan slider value display (non interactive!)
- 4. volume slider
- 5. volume slider value display (non interactive!)
- 6. `ENV_1` -> `OSC_N` VCA modulation
- 7. `ENV_2` -> `OSC_N` VCA modulation
- 8. `ENV_3` -> `OSC_N` VCA modulation
- 9. `ENV_4` -> `OSC_N` VCA modulation
- 10. `OSC_1` -> `OSC_N` VCA modulation
- 11. `OSC_2` -> `OSC_N` VCA modulation
- 12. `OSC_3` -> `OSC_N` VCA modulation
- 13. `OSC_4` -> `OSC_N` VCA modulation
- 14. `OSC_5` -> `OSC_N` VCA modulation
- 15. `OSC_6` -> `OSC_N` VCA modulation
- 16. `OSC_7` -> `OSC_N` VCA modulation


modulation sliders, like pan sliders, are bidirectional: 
- at the center, the modulator does not contribute to the amplitude of the carrier
- at 100% right, the full amount of the modulator signal is *added* to the amplitude of the carrier
- at 100% left, the modulation is *inverted*: the modulator is subtracted from the carrier's level, and a proportional constant offset is added.

## ROADMAP

in rough priority order:

- add controls to seek to different parts of the recorded input
- add preset import / export
- add controls to introduce live input
- visualize / scope signals
- morph presets
- control morph from MIDI

and in parallel, of course, make any needed changes / refinements to the sound!