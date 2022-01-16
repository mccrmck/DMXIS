DMXIS {

	classvar <cues, <reactDict, <>vst;

	*initClass {

		StartUp.add{

			cues = IdentityDictionary();
			reactDict = IdentityDictionary();

			SynthDef(\dmxis,{
				Out.ar(
					\outBus.kr(0),
					VSTPlugin.ar(numOut: 1))
			}).add;

			SynthDef(\dmxisAmpReactOneChan,{                 // eventually create a dictionary of synths that can send arrays of args, not just amp
				var sig = SoundIn.ar(\inBus.kr(0));
				var amp = Amplitude.kr(sig);

				amp = Lag3.kr(amp,\lagTime.kr(0.1));    // consider Lag3UD.kr
				SendReply.kr(Impulse.kr(\trigRate.kr(8)),'/setLightOneChan',[amp]);

			}).add;
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
		^preset
	}

	*loadFromMIDI { |key, path, loopKey|
		var pathToMIDI = path.asString;

		if(pathToMIDI.extension == "mid",{
			var file = SimpleMIDIFile.read(pathToMIDI)
			.timeMode_(\seconds)
			.midiEvents;

			var fileOn = file.reject({ |event| event[2] == \noteOff });

			var times = fileOn.collect({ |event| event[1] });
			var presets = fileOn.collect({ |event| event[4] / 100 });

			// must calculate the last delta!
			if(file.size == 1,{
				var lastDelta = file.last[1] - file[file.size-2][1];

				times[0] = lastDelta
			},{
				var lastDelta = file.last[1] - file[file.size-2][1];

				times = times.differentiate.drop(1);

				times = times.add(lastDelta);
			});

			cues.put(key.asSymbol,
				(
					times: times,
					presets: presets,
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
			var times = cues[uniqueKey]['times'];
			var presets = cues[uniqueKey]['presets'];

			if(loopKey.notNil,{
				var pattern = Pdef("%Loop".format(uniqueKey).asSymbol,
					Pbind(
						\type,\vst_set,
						\vst,vst,
						\params,[ \Preset ],
						\dur,Pseq( times, inf),
						\Preset,Pwhile({ cues[uniqueKey].at(loopKey.asSymbol) }, Pseq( presets, 1 )),
					)
				);
				cues[uniqueKey].put('pattern',pattern);
			},{
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
			});
		},{
			"DMXIS-VST not running".throw;
		});

		^cues[uniqueKey]['pattern']
	}

	*react { |uniqueKey, in, param|           // eventually add a type arg that allows for choosing different synths -
		var pattern = this.makeReactPat(uniqueKey,in);
		var oscFunc = this.makeReactOSCFunc(param);

		reactDict.put(uniqueKey.asSymbol,() );
		reactDict[uniqueKey.asSymbol]
		.put('pattern',pattern)
		.put('oscFunc',oscFunc);

		^pattern;
	}

	*makeReactPat { |key, in|

		var pat = Pdef(key.asSymbol,
			Pmono(\dmxisAmpReactOneChan,
				\dur,1,
				\inBus,in,
				\lagTime,0.5,
				\trigRate,8,
			)
		);
		^pat
	}

	*makeReactOSCFunc { |param|
		var func = 	OSCFunc({ |msg, time, addr, recvPort|
			var val = msg[3];

			// val.postln;

			DMXIS.vst.set(param.asSymbol,val);

		},'/setLightOneChan');
		^func
	}

	*cleanUpReact { |uniqueKey, param|

		var pat = Pdef("%CleanUp".format(uniqueKey).asSymbol,
			Prout({
				reactDict[uniqueKey]['pattern'].stop;
				// vst.set(param.asSymbol,0); //turn down the fader we were affecting!
				reactDict[uniqueKey]['oscFunc'].clear;
				reactDict[uniqueKey]['pattern'].clear;
				reactDict[uniqueKey] = nil;
			})
		);
		^pat
	}

}

