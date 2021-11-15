DMXIS {

	classvar <cues, <>vst;

	*initClass {

		StartUp.add{

			cues = IdentityDictionary();

			SynthDef(\dmxis,{
				Env.asr().kr(2,\gate.kr(1));
				Out.ar(
					\outBus.kr(0),
					VSTPlugin.ar(numOut: 1))
			}).add
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
		this.vst.synth.free;
	}

	*setPreset { |preset|
		var pSet = preset / 100;
		vst.set(\Preset,pSet);
		"Preset: %".format(preset).postln;
		^preset
	}

	*loadFromMIDI { |key, path|
		var pathToMIDI = path.asString;

		if(pathToMIDI.extension == "mid",{
			var file = SimpleMIDIFile.read(pathToMIDI)
			.timeMode_(\seconds)
			.midiEvents
			.reject({ |event| event[2] == \noteOff });

			var times = file.collect({ |event| event[1] }).differentiate.drop(1);
			var presets = file.collect({ |event| event[4] });

			cues.put(key.asSymbol,
				(
					times: times,
					presets: presets
				)
			);

		},{
			"bad path, must be a .mid file!".throw;
		});

		^cues.at(key)
	}

	*makePat { |key, path = nil|
		var uniqueKey = key.asSymbol;

		if(path.isNil,{
			if(cues[uniqueKey].isNil,{
				"no file loaded".throw;
			});
		},{
			this.loadFromMIDI(key, path);
		});

		if(vst.isNil.not,{
			var times = cues[uniqueKey]['times'];
			var presets = cues[uniqueKey]['presets'];

			var pattern = Pdef(uniqueKey,
				Pbind(
					\type,\vst_set,
					\vst,vst,
					\params,[ \Preset ],
					\dur,Pseq( times, 1 ),
					\Preset,Pseq( presets, 1 ),
				)
			);

			cues[uniqueKey].put('pattern',pattern)
		},{
			"DMXIS-VST not running".throw;
		});

		^cues[uniqueKey]['pattern']
	}
}