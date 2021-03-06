import {AbstractController} from './server/AbstractController';
import {ProjectController} from '../project/ProjectController';
import {AuthenticationController} from '../authentication/AuthenticationController';
import {UserController} from '../users/UserController';
import {IServiceMap} from './IServiceMap';
import {DatastoreController} from '../data/DatastoreController';
import {HealthController} from './server/HealthController';
import {IApiConfig} from '../IApiConfig';

export interface IControllerMap {
    [k: string]: AbstractController;

    health: HealthController;
    project: ProjectController;
    authentication: AuthenticationController;
    user: UserController;
    datastore: DatastoreController;
}

export function getControllers(services: IServiceMap, config: IApiConfig): IControllerMap {
    return {
        health: new HealthController(services),
        project: new ProjectController(services.project),
        authentication: new AuthenticationController(services.authentication),
        user: new UserController(services.authentication, services.user, services.datastore),
        datastore: new DatastoreController(services.datastore, config),
    };
}
