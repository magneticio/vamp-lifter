import {EventEmitter, Injectable} from '@angular/core';
import {Subject} from 'rxjs/Subject';

export class ToolbarAction {
  $this: any;
  icon: string;
  tooltip: string;
  click: any;

  constructor($this: any, icon: string, tooltip: string, click: any) {
    this.$this = $this;
    this.icon = icon;
    this.tooltip = tooltip;
    this.click = click;
  }
}

@Injectable()
export class ToolbarService {

  actions: Subject<Array<ToolbarAction>> = new EventEmitter();
  progress: Subject<boolean> = new EventEmitter();

  constructor() {
  }

  progressStart() {
    this.progress.next(true);
  }

  progressStop() {
    this.progress.next(false);
  }
}
