import {Component, OnDestroy, OnInit} from '@angular/core';
import {MapService} from '../../lib/map/map.service';
import {Subscription} from 'rxjs';
import {RxUtils} from '../../lib/utils/RxUtils';
import {LoggerFactory} from '../../lib/utils/LoggerFactory';
import {ProjectService} from '../../lib/project/project.service';
import {DrawEvent, OlEvent, olFromLonLat, OlMap, OlVectorSource, OlView} from '../../lib/OpenLayersImports';
import {OpenLayersHelper} from '../../lib/map/OpenLayersHelper';
import {DrawingTool, DrawingTools} from '../../lib/map/DrawingTool';
import {Actions, ofType} from '@ngrx/effects';
import {MapModule} from '../../store/map/map-actions';
import {IAbcStyleContainer} from '../../lib/map/AbcStyles';
import * as _ from 'lodash';
import {IMainState} from '../../store';
import {Store} from '@ngrx/store';
import {flatMap, map, take} from 'rxjs/operators';
import {zip} from 'rxjs/internal/observable/zip';
import {of} from 'rxjs/internal/observable/of';
import {IProject} from 'abcmap-shared';
import ActionTypes = MapModule.ActionTypes;
import ActiveForegroundColorChanged = MapModule.ActiveForegroundColorChanged;
import ActiveBackgroundColorChanged = MapModule.ActiveBackgroundColorChanged;


@Component({
  selector: 'abc-main-map',
  templateUrl: './main-map.component.html',
  styleUrls: ['./main-map.component.scss']
})
export class MainMapComponent implements OnInit, OnDestroy {

  private logger = LoggerFactory.new('MainMapComponent');

  map?: OlMap;

  project$?: Subscription;
  drawingTool$?: Subscription;
  colorChanged$?: Subscription;

  currentStyle: IAbcStyleContainer = {
    foreground: 'rgba(0,0,0)',
    background: 'rgba(0,0,0)',
    strokeWidth: 7,
  };

  constructor(private mapService: MapService,
              private actions$: Actions,
              private store: Store<IMainState>,
              private projectService: ProjectService) {
  }

  ngOnInit() {
    this.setupMap();
    this.updateLayersWhenProjectLoaded();
    this.updateDrawingInteractionWhenActiveLayerChange();
    this.listenDrawingToolState();
    this.listenStyleState();
    this.initStyle();
  }

  ngOnDestroy() {
    RxUtils.unsubscribe(this.project$);
    RxUtils.unsubscribe(this.drawingTool$);
    RxUtils.unsubscribe(this.colorChanged$);
  }

  setupMap() {
    this.map = new OlMap({
      target: 'main-openlayers-map',
      layers: [],
      view: new OlView({
        center: olFromLonLat([37.41, 8.82]),
        zoom: 4,
      }),
    });
  }

  updateLayersWhenProjectLoaded() {
    this.project$ = this.projectService.listenProjectLoaded()
      .subscribe(project => {
        if (!this.map || !project) {
          return;
        }
        this.logger.info('Updating layers ...');

        this.mapService.removeLayerSourceChangedListener(this.map, this.onLayerSourceChange);
        this.mapService.updateLayers(project, this.map);
        this.mapService.setDrawingTool(DrawingTools.None);
        this.mapService.addLayerSourceChangedListener(this.map, this.onLayerSourceChange);
      });
  }

  updateDrawingInteractionWhenActiveLayerChange() {
    this.projectService.listenProjectState()
      .pipe(
        flatMap(tool =>
          zip(
            this.mapService.listenMapState().pipe(take(1), map(mapState => mapState.drawingTool)),
            of(tool)
          ))
      )
      .subscribe(([tool, project]) => {
        this.setDrawingTool(tool, project);
      });

  }

  listenDrawingToolState() {
    this.drawingTool$ = this.mapService.listenDrawingToolChanged()
      .pipe(
        flatMap(tool =>
          zip(
            of(tool),
            this.projectService.listenProjectState().pipe(take(1))
          ))
      )
      .subscribe(([tool, project]) => {
        this.setDrawingTool(tool, project);
      });
  }

  onDrawEnd = (event: DrawEvent) => {
    OpenLayersHelper.setStyle(event.feature, _.cloneDeep(this.currentStyle));
  };

  onLayerSourceChange = (event: OlEvent) => {
    if (event.target instanceof OlVectorSource) {
      const source: OlVectorSource = event.target;
      const geojsonFeatures = this.mapService.featuresToGeojson(source.getFeatures());
      const layerId = OpenLayersHelper.getLayerId(source);

      this.projectService.updateVectorLayer(layerId, geojsonFeatures);
    }
  };

  listenStyleState() {
    this.colorChanged$ = this.actions$
      .pipe(
        ofType(
          ActionTypes.ACTIVE_FOREGROUND_COLOR_CHANGED,
          ActionTypes.ACTIVE_BACKGROUND_COLOR_CHANGED,
        ),
      )
      .subscribe((action: ActiveForegroundColorChanged | ActiveBackgroundColorChanged) => {
        if (action.type === ActionTypes.ACTIVE_FOREGROUND_COLOR_CHANGED) {
          this.currentStyle.foreground = action.color;
        } else {
          this.currentStyle.background = action.color;
        }
      });
  }

  initStyle() {
    this.store
      .select(state => state.map.activeStyle)
      .pipe(take(1))
      .subscribe(style => {
        this.currentStyle = style;
      });
  }

  setDrawingTool(tool: DrawingTool, project: IProject | undefined) {
    if (!this.map || !project || !project.activeLayerId) {
      return;
    }

    this.logger.info('Updating drawing tool ...');

    const layer = OpenLayersHelper.findVectorLayer(this.map, project.activeLayerId);
    if (!layer) {
      this.mapService.removeAllDrawInteractions(this.map);
    } else {
      this.mapService.setDrawInteractionOnMap(tool, this.map, layer, this.onDrawEnd);
    }
  }
}
