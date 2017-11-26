import {MapService} from "./services/MapService";
import {Ipc} from "./ipc/Ipc";
import {Logger} from "./dev/Logger";
import {ProjectService} from "./services/ProjectService";
import {Subj} from "./ipc/IpcSubjects";
import {EntitiesUtils} from "./utils/EntitiesUtils";

const logger = Logger.getLogger('api/main.ts');
const eu = new EntitiesUtils();

export function initApplication(ipc: Ipc) {

    logger.info('Initialize main application');

    const projectService = new ProjectService(ipc);
    const mapService = new MapService(ipc);

    ipc.listen(Subj.PROJECT_CREATE_NEW, () => {
        projectService.newProject();
    });

    ipc.listen(Subj.PROJECT_GET_CURRENT, () => {
        return projectService.getCurrentProject();
    });

    ipc.listen(Subj.MAP_GET_WMS_DEFAULT_LAYERS, () => {
        return mapService.getDefaultWmsLayers();
    });

    projectService.newProject();
}