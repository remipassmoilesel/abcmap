import {ActionReducer, MetaReducer} from '@ngrx/store';
import {environment} from '../../environments/environment';
import {projectReducer} from './project/project-reducers';
import {IProjectState} from './project/project-state';
import {IMapState} from "./map/map-state";
import {mapReducer} from "./map/map-reducers";
import {IGuiState} from './gui/gui-state';
import {guiReducer} from './gui/gui-reducers';

export interface IMainState {
  project: IProjectState;
  map: IMapState;
  gui: IGuiState;
}

export const reducers: any = { // TODO: fix any, normally ActionReducerMap<State>  ?
  project: projectReducer,
  map: mapReducer,
  gui: guiReducer
};

export function debug(reducer: ActionReducer<any>): ActionReducer<any> {
  return function (state, action) {
    console.log('state', state);
    console.log('action', action);

    return reducer(state, action);
  };
}

export const metaReducers: MetaReducer<IMainState>[] = !environment.production ? [] : []; // [debug]
