DMXIS {

	classvar <cues, <>vst;

	*initClass {

		StartUp.add{

			cues = IdentityDictionary();

			SynthDef(\dmxis,{
				OffsetOut.ar(
					\outBus.kr(0),
					VSTPlugin.ar(numOut: 1))
			}).add;

			// make a react synth?
			// make a random algo synth??

		}
	}

	*new {
		^super.new.init;
	}

	init {
		vst = VSTPluginController( Synth(\dmxis) ).open("DMXIS-Inst.vst");
		^vst;
	}

	*free {
		vst.synth.free;
	}

	*setPreset { |preset|
		var pSet = preset / 100;
		vst.set(\Preset,pSet);
		"Preset: %".format(preset).postln;
		^vst
	}

	*loadFromMIDI { |key, path, loopKey|
		var pathToMIDI = path.asString;

		if(pathToMIDI.extension == "mid",{
			var file = SimpleMIDIFile.read(pathToMIDI)
			.timeMode_(\seconds)
			.midiEvents;

			var notes = file.reject({ |event| event[2] == 'cc' });
			var control = file.select({ |event| event[2] == 'cc' });
			var noteData = Order();

			128.do({ |i|
				var num  = notes.select({ |event| event[4] == i });

				if( num.size > 0,{
					var times = num.collect({ |event| event[1] }).differentiate.add(0.0);
					var chans = num.collect({ |event| event[3] });
					var vals  = num.collect({ |event| event[5] });

					noteData.put(i,
						(
							nTimes: times,
							nChans: chans,
							nVals:  vals
						)
					);
				})
			});

			cues.put(key.asSymbol,
				(
					noteData: noteData,
					cTimes: control.collect({ |event| event[1] }).differentiate.add(0.0),
					cChans: control.collect({ |event| event[3] }),
					cNums:  control.collect({ |event| event[4] }),
					cVals:  control.collect({ |event| event[5] }),
				)
			);

			if(loopKey.notNil,{ cues[key.asSymbol].put(loopKey.asSymbol, true) });

		},{
			"bad path, must be a .mid file!".throw;
		});

		^cues.at(key)
	}

	*makePat { |key, path = nil, loopKey|
		var uniqueKey = key.asSymbol;

		if(path.isNil,{
			if(cues[uniqueKey].isNil,{
				"no file loaded".throw;
			});
			loopKey = cues[uniqueKey].findKeyForValue(true);                      // this doesn't seem that strong, could improve!
		},{
			this.loadFromMIDI(uniqueKey, path, loopKey);
		});

		if(vst.notNil,{
			var cue = cues[uniqueKey];

			if(loopKey.notNil,{
				var pattern = Pwhile({ cues[uniqueKey].at(loopKey.asSymbol) },
					Ppar(

						cue['noteData'].indices.collect({ |num|
							var times = cue['noteData'][num]['nTimes'];
							var chans = cue['noteData'][num]['nChans'];
							var vals  = cue['noteData'][num]['nVals'];

							Pseq([

								Pbind(
									\dur, Pseq([ times[0] ]),
									\note, Rest()
								),
								Pbind(
									\type,Pseq([\vst_midi,\rest],inf),
									\vst,vst,
									\dur,Pseq( times[1..] ),   // times
									\legato, 0.99,
									\midicmd, \noteOn,        // cmds
									\chan,Pseq( chans ),      // chans

									\midinote, num,  // nums
									\amp, Pseq( vals / 127 ), // vals
								)
							])
						})

						++

						Pseq([
							Pbind(
								\dur, Pseq([ cue['cTimes'][0] ]),
								\note, Rest()
							),
							Pbind(
								\type,\vst_midi,
								\vst,vst,
								\dur,Pseq( cue['cTimes'][1..] ), // times
								\midicmd, \control,  // cmds
								\chan,Pseq( cue['cChans'] ),     // chans
								\ctlNum, Pseq( cue['cNums'] ),  // nums
								\control, Pseq( cue['cVals'] ), // vals
							)
						])
					);
				);

				cue.put('pattern',pattern)
			},{
				var pattern = Ppar(

					cue['noteData'].indices.collect({ |num|
						var times = cue['noteData'][num]['nTimes'];
						var chans = cue['noteData'][num]['nChans'];
						var vals  = cue['noteData'][num]['nVals'];

						Pseq([
							/*Pbind(
							\dur, Pseq([ times[0] ]),
							\note, Rest()
							),*/
							Pbind(
								\type,Pseq([\vst_midi,\rest],inf),
								\vst,vst,
								\dur,Pseq( times[1..] ),   // times
								\legato, 0.99,
								\midicmd, \noteOn,        // cmds
								\chan,Pseq( chans ),      // chans

								\midinote, num,  // nums
								\amp, Pseq( vals / 127 ), // vals
							)
						])
					})

					++

					Pseq([
						Pbind(
							\dur, Pseq([ cue['cTimes'][0] ]),
							\note, Rest()
						),
						Pbind(
							\type,\vst_midi,
							\vst,vst,
							\dur,Pseq( cue['cTimes'][1..] ), // times
							\midicmd, \control,  // cmds
							\chan,Pseq( cue['cChans'] ),     // chans
							\ctlNum, Pseq( cue['cNums'] ),  // nums
							\control, Pseq( cue['cVals'] ), // vals
						)
					])
				);

				cue.put('pattern',pattern)
			});
		},{
			"DMXIS-VST not running, lights not loaded".postln;
		});

		^cues[uniqueKey]['pattern']
	}
}