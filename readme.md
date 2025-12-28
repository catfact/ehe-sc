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

## ROADMAP

in rough priority order:

- [ ] configure server for multichannel input; device selection (config file?)
- [ ] add controls for morph rate / duration
- [ ] cross-fade frequency changes instead of glissando
- [ ] "performance mode" UI: simplified controls, basic level / clip indicators, prettier
- [x] add controls to seek to different parts of the recorded input
- [x] add preset import / export
- [x] add controls to introduce live input
- [x] visualize / scope signals
- [x] morph presets
- [ ] control morph from MIDI

and in parallel, of course, make any needed changes / refinements to the sound!
