import {Component, OnInit} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {environment} from '../../environments/environment';
import {ToolbarAction, ToolbarService} from '../toolbar/toolbar.service';
import {isNullOrUndefined} from "util";

@Component({
  selector: 'app-connections',
  templateUrl: './connections.component.html',
  styleUrls: ['./connections.component.scss']
})
export class ConnectionsComponent implements OnInit {

  sections: Array<Section>;

  constructor(private http: HttpClient, private toolbar: ToolbarService,) {
  }

  ngOnInit() {
    this.toolbar.actions.next([
      new ToolbarAction(this, 'refresh', 'Refresh', ($this) => $this.check())
    ]);
    this.check();
  }

  private reset() {
    this.sections = [
      new Section('key_value', 'key-value store'),
      new Section('persistence', 'persistence'),
      new Section('pulse', 'pulse'),
      new Section('container_scheduler', 'container scheduler')
    ];
  }

  private check() {
    this.reset();
    this.http.get(environment.api('connections')).subscribe((info) => {
      this.extract('key_value', () => info['key_value'].type);
      this.extract('persistence', () => info['persistence'].database.type);
      this.extract('pulse', () => info['pulse'].type);
      this.extract('container_scheduler', () => info['container_driver'].type);
    }, () => {
      this.sections.forEach((section) => {
        section.ok = false;
        section.error = true;
      });
    });
  }

  private extract(id: string, pull: () => any) {
    const section = this.sections.find((section) => section.id === id);
    try {
      const message = pull();
      if (!isNullOrUndefined(message)) {
        section.message = message;
        section.ok = true;
        section.error = false;
        return;
      }
    } catch (e) {
    }
    if (section) {
      section.ok = false;
      section.error = true;
    }
  }
}

class Section {

  id: string;
  message: string;

  ok: boolean;
  error: boolean;

  constructor(id: string, message: string) {
    this.id = id;
    this.message = message;
    this.ok = false;
    this.error = false;
  }

  progress() {
    return !(this.ok || this.error);
  }
}
