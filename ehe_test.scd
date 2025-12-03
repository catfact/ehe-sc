e = EHE.ehe;

e.z[\src][0].run(true);
e.buf.do ({ arg bf; bf.plot; });

e.b[\src][0].scope;
e.b[\src][1].scope;
e.b[\src][2].scope;
e.b[\src][3].scope;

e.b[\env][0].scope;
e.b[\env][1].scope;
e.b[\env][2].scope;
e.b[\env][3].scope;

e.b[\osc][0].scope;
e.b[\osc][1].scope;
e.b[\osc][2].scope;
e.b[\osc][3].scope;
e.b[\osc][4].scope;
e.b[\osc][5].scope;
e.b[\osc][6].scope;

e.z[\vca_vca][0][4].set(\c, 16.dbamp);
e.z[\vca_vca][1][5].set(\c, 16.dbamp);
e.z[\vca_vca][2][6].set(\c, 16.dbamp);

e.b[\vca_cv][0].scope;
e.b[\vca_cv][1].scope;
e.b[\vca_cv][2].scope;
e.b[\vca_cv][3].scope;
e.b[\vca_cv][4].scope;
e.b[\vca_cv][5].scope;
e.b[\vca_cv][6].scope;

e.b[\vca_out][0].scope;
e.b[\vca_out][1].scope;

e.b[\vca_out][2].scope;
e.b[\vca_out][3].scope;
e.b[\vca_out][4].scope;
e.b[\vca_out][5].scope;
e.b[\vca_out][6].scope;

// b[\osc_mod][0].scope;
// b[\osc_mod][1].scope;
// b[\osc_mod][2].scope;
// b[\osc_mod][3].scope;
// b[\osc_mod][4].scope;
// b[\osc_mod][5].scope;
// b[\osc_mod][6].scope;

e.z[\mix][0].set(\level, 1);
e.z[\mix][1].set(\level, 1);
e.z[\mix][2].set(\level, 1);
e.z[\mix][3].set(\level, 1);
e.z[\mix][4].set(\level, 1);
e.z[\mix][5].set(\level, 1);
e.z[\mix][6].set(\level, 1);

b[\mix].scope;


z[\osc].do({ arg osc; osc.set(\feedback, 2.0); });
z[\osc].do({ arg osc; osc.set(\feedback, 4/7); });