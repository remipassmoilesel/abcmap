import * as Resurrect from 'resurrect-js';
import {Project} from "../entities/Project";
import {WmsLayer} from "../entities/WmsLayer";
import {IpcEvent, IpcEventImpl} from "../ipc/IpcEvent";
import {Evt} from "../ipc/IpcEventTypes";

// List of constructors used to deserialize objects
const constructors: any = {};
constructors.WmsLayer = WmsLayer;
constructors.Project = Project;
constructors.IpcEventImpl = IpcEventImpl;
constructors.Evt = Evt;

export class EntitiesUtils {

    private necromancer: any;

    constructor() {
        this.necromancer = new Resurrect({
            resolver: new Resurrect.NamespaceResolver(constructors), // resolve constructors from map above
            cleanup: true, // remove special fields from created objects
        });
    }

    public serialize(data: any): string {
        return this.necromancer.stringify(data);
    }

    public deserialize(serialized: string): any {

        // do not use resurrect for empty objects
        // in order to avoid unhandled rejection errors
        if (serialized === '{}') {
            return {};
        } else {
            return this.necromancer.resurrect(serialized);
        }
        
    }

    public deserializeProject(data: string): Project {
        return this.deserialize(data);
    }

    public deserializeIpcEvent(data: string): IpcEvent {
        return this.deserialize(data);
    }

}
