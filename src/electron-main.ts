import {app, BrowserWindow} from 'electron';
import * as url from 'url';
import {ElectronUtilities} from "./api/dev/ElectronDevUtilities";
import {Logger} from "./api/dev/Logger";
import {closeApplication, initApplication} from "./api/main";
import {Ipc} from "./api/ipc/Ipc";

require('source-map-support').install();

const paths = require('../config/paths');
const logger = Logger.getLogger('electron-main.ts');

// Keep a global reference of the window object, if you don't, the window will
// be closed automatically when the JavaScript object is garbage collected.
let win: BrowserWindow | null;

// listen for unhandled rejections and uncaught errors
process.on('unhandledRejection', (reason, p) => {
    logger.error(`[UNHANDLED REJECTION ERROR] Reason=${JSON.stringify(reason)} P=${JSON.stringify(p)}`);
});

process.on('uncaughtException', (err) => {
    logger.error(`[UNCAUGHT ERROR] Err=${JSON.stringify(err)}`);
});

function createWindow() {
    // Create the browser window.
    win = new BrowserWindow({width: 1024, height: 768});
    win.maximize();

    // init api application
    const ipc = new Ipc(win.webContents);
    initApplication(ipc);

    // and load the index.html of the app.
    win.loadURL(url.format({
        pathname: paths.INDEX_DEST,
        protocol: 'file:',
        slashes: true
    }));

    if (ElectronUtilities.isDevMode()) {
        logger.info('Dev mode enabled, installing dev tools');
        ElectronUtilities.setupDevTools();
    }

    // Open the DevTools.
    setTimeout(() => {
        win.webContents.openDevTools();
    }, 500);

    // Emitted when the window is closed.
    win.on('closed', () => {
        // Dereference the window object, usually you would store windows
        // in an array if your app supports multi windows, this is the time
        // when you should delete the corresponding element.
        win = null;
    });

}

// This method will be called when Electron has finished
// initialization and is ready to create browser windows.
// Some APIs can only be used after this event occurs.
app.on('ready', createWindow);

// Quit when all windows are closed.
app.on('window-all-closed', () => {

    logger.info('All windows are closed, shutting down app ...');

    closeApplication()
        .then(() => {
            logger.info('Database closed');
            app.exit();
        })
        .catch((error) => {
            logger.error('Error while closing app: ', error);
            app.exit(1);
        });

});
