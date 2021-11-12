DMXIS {

	classvar <>vst, <pattern;

	*initClass {

		StartUp.add{

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

	*setPreset { |preset|
		var pSet = preset/100;
		vst.set(\Preset,pSet);
		"Preset: %".format(preset).postln;
		^preset
	}

	*loadPat { |uniqueKey, pathToMIDI|

		if( pathToMIDI.asString.extension == "mid",{
			var file = SimpleMIDIFile.read(pathToMIDI.asString)
			.timeMode_(\seconds)
			.midiEvents
			.reject({ |event| event[2] == \noteOff});

			var times = file.collect({ |event| event[1] }).differentiate.drop(1);
			var presets = file.collect({ |event| event[4] });

			pattern = Pdef(uniqueKey.asSymbol,
				Pbind(
					\type,\vst_set,
					\vst,vst,
					\params,[ \Preset ],
					\dur,Pseq( times ,1),
					\Preset,Pseq( presets ,1),
				)
			);
		},{
			"bad path, must be a .mid file!".warn;
		});

		^pattern
	}

	*play { |uniqueKey, pathToMIDI|

		this.loadPat(uniqueKey, pathToMIDI).play; // does this need to return something special???
	}


}

// everything needs to be class methods - not going to run more than one instance of a DMX VST, right? Right?!

.clickKeys