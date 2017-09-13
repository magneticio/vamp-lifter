import {Component, EventEmitter, OnInit, Output} from '@angular/core';
import {ToolbarAction, ToolbarService} from "./toolbar.service";
import {NavigationEnd, Router} from "@angular/router";

@Component({
  selector: 'app-toolbar',
  templateUrl: './toolbar.component.html',
  styleUrls: ['./toolbar.component.scss']
})
export class ToolbarComponent implements OnInit {

  actions: Array<ToolbarAction> = [];
  progress: boolean = false;

  @Output() onMenu: EventEmitter<any> = new EventEmitter();

  constructor(private router: Router, private toolbar: ToolbarService) { }

  ngOnInit() {
    this.router.events.filter((event) => event instanceof NavigationEnd).subscribe(() => {
      setTimeout(() => {
        this.actions.length = 0;
        this.toolbar.progressStop();
      });
    });
    this.toolbar.actions.subscribe((actions: Array<ToolbarAction>) => setTimeout(() => this.actions = actions));
    this.toolbar.progress.subscribe((enable: boolean) => {
      this.progress = enable;
    });
  }

  onMenuClicked(event: any): void {
    this.onMenu.emit(event);
  }
}
