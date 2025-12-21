m = EHE_state_morph.new;
m.init;

/*
EHE_state.write_state_to_file(EHE_state.new_state_from_gui(EHE.gui), "~/Desktop/earth_horns/ehe_sc/presets/default.scd".standardizePath);

EHE_state.write_state_to_file(EHE_state.new_state_from_gui(EHE.gui), "~/Desktop/earth_horns/ehe_sc/presets/ehe-1.scd".standardizePath);

EHE_state.write_state_to_file(EHE_state.new_state_from_gui(EHE.gui), "~/Desktop/earth_horns/ehe_sc/presets/ehe-2.scd".standardizePath);

EHE_state.write_state_to_file(EHE_state.new_state_from_gui(EHE.gui), "~/Desktop/earth_horns/ehe_sc/presets/ehe-3.scd".standardizePath);
*/

~p1 = "~/Desktop/earth_horns/ehe_sc/presets/ehe-1.scd".standardizePath;
~p2 = "~/Desktop/earth_horns/ehe_sc/presets/ehe-2.scd".standardizePath;
~p3 = "~/Desktop/earth_horns/ehe_sc/presets/ehe-3.scd".standardizePath;


x = EHE_state.read_state_from_file(~p1);
y = EHE_state.read_state_from_file(~p2);
z = EHE_state.read_state_from_file(~p3);

//z = EHE_state_morph.new_morphed_state(x, y, 0.5);

m.r = 0.1;
m.t = 0;

m.morph_to(x);
m.morph_to(y);
m.morph_to(z);
m.isMorphing = true;