DMXIS {

	classvar <cues, <loopCues, <>vst;

	*initClass {

		StartUp.add{

			cues = IdentityDictionary();
			loopCues = IdentityDictionary();

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
		vst = VSTPluginController( Synth(\dmxis) ).open("DMXIS-Inst.vst");            // add set preset to the action function?
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

	*loadFromMIDI { |key, path, loop = false|
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
					var vals  = num.collect({ |event| event[5] ? 0 });

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
					cChans: control.collect({ |event| event[3] }).add(0.0),
					cNums:  control.collect({ |event| event[4] }).add(0.0),
					cVals:  control.collect({ |event| event[5] }).add(0.0),
				)
			);

			if(loop,{ loopCues.put(key.asSymbol, true) });

		},{
			"bad path, must be a .mid file!".throw;
		});

		^cues.at(key)
	}

	*makePat { |key, path = nil, loop = false|
		var uniqueKey = key.asSymbol;

		if(path.isNil,{
			if(cues[uniqueKey].isNil,{
				"no file loaded".throw;
			});
			loop = loopCues[uniqueKey];
		},{
			this.loadFromMIDI(uniqueKey, path, loop);
		});

		if(vst.notNil,{
			var cue = cues[uniqueKey];

			var notePats = cue['noteData'].indices.collect({ |num|
				var times = cue['noteData'][num]['nTimes'];
				var chans = cue['noteData'][num]['nChans'];
				var vals  = cue['noteData'][num]['nVals'];

				Pseq([
					Pbind(
						\dur, Pseq([ times[0] ]),
						\note, Rest()
					),
					Pbind(
						\type,Pseq([\vst_midi,\rest],inf), // manage with a Pfunc? load cmds, if(Pkey(\cmd) == noteOn, {type = \vst_midi},{type = \rest})
						\vst,vst,
						\dur,Pseq( times[1..] ),  // times
						\legato, 0.999,
						\midicmd, \noteOn,        // cmds
						\chan,Pseq( chans ),      // chans
						\midinote, num,           // nums
						\amp, Pseq( vals / 127 ), // vals
					)
				])
			});

			var ccPat = if( cue['cTimes'].size > 1,{

				Pseq([
					Pbind(
						\dur, Pseq([ cue['cTimes'][0] ]),
						\note, Rest()
					),
					Pbind(
						\type,\vst_midi,
						\vst,vst,
						\dur,Pseq( cue['cTimes'][1..] ), // times
						\midicmd, \control,              // cmds
						\chan,Pseq( cue['cChans'] ),     // chans
						\ctlNum, Pseq( cue['cNums'] ),   // nums
						\control, Pseq( cue['cVals'] ),  // vals
					)
				]);
			},{
				Pbind(
					\dur, Pseq([ cue['cTimes'][0] ]),
					\note, Rest()
				)
			});

			var pattern = Ppar( notePats ++ ccPat );

			if(loop,{ pattern = Pwhile({ loopCues.at(uniqueKey.asSymbol) }, pattern ) });

			cue.put('pattern',pattern)
		},{
			"DMXIS-VST not running, lights not loaded".postln;
		});

		^cues[uniqueKey]['pattern']
	}

	*makePresetPat { |uniqueKey, delta, preset |
		var key = (uniqueKey ++ "Pset").asSymbol;

		var pattern = Pbind(
			\type,\vst_set,
			\vst,vst,
			\params,[ \Preset ],
			\dur,Pseq( delta.asArray ),
			\Preset,Pseq( preset.asArray / 100 ),
		);

		cues.put(key, pattern);

		^cues[key]
	}
}