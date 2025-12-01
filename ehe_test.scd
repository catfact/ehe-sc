b[\src][0].scope;
b[\src][1].scope;
b[\src][2].scope;
b[\src][3].scope;

b[\env][0].scope;
b[\env][1].scope;
b[\env][2].scope;
b[\env][3].scope;

b[\osc][0].scope;
b[\osc][1].scope;
b[\osc][2].scope;
b[\osc][3].scope;
b[\osc][4].scope;
b[\osc][5].scope;
b[\osc][6].scope;

b[\osc_mod][0].scope;
b[\osc_mod][1].scope;
b[\osc_mod][2].scope;
b[\osc_mod][3].scope;
b[\osc_mod][4].scope;
b[\osc_mod][5].scope;
b[\osc_mod][6].scope;

z[\mix][0].set(\level, 1);
z[\mix][1].set(\level, 1);
z[\mix][2].set(\level, 1);
z[\mix][3].set(\level, 1);
z[\mix][4].set(\level, 1);
z[\mix][5].set(\level, 1);
z[\mix][6].set(\level, 1);

b[\mix].scope;

z[\osc].do({ arg osc; osc.set(\feedback, 2.0); });
z[\osc].do({ arg osc; osc.set(\feedback, 4/7); });