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

  constructor(private http: HttpClient, private toolbar: ToolbarService) {
  }

  ngOnInit() {
    this.check();
    this.toolbar.actions.next([
      new ToolbarAction(this, 'refresh', 'Refresh', ($this) => $this.check())
    ]);
  }

  private reset() {
    this.sections = [
      new Section('key_value', 'key-value store', ''),
      new Section('persistence', 'persistence', ''),
      new Section('pulse', 'pulse', ''),
      new Section('container_scheduler', 'container scheduler', '')
    ];
  }

  private check() {
    this.reset();
    setTimeout(() => {
      this.toolbar.progressStart();
      this.pull();
    });
  }

  private pull() {
    this.http.get(environment.api('connections')).subscribe((info) => {
      this.extractAll(info);
      this.toolbar.progressStop();
    }, (response) => {
      this.extractAll(response.error);
      this.toolbar.progressStop();
    }, () => this.toolbar.progressStop());
  }

  private extractAll(response: any) {
    this.extract('key_value', () => response['key_value'].type);
    this.extract('persistence', () => response['persistence'].database.type);
    this.extract('pulse', () => response['pulse'].type);
    this.extract('container_scheduler', () => response['container_driver'].type);
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
      section.message = '<connection error>';
    }
  }
}

class Section {

  id: string;
  label: string;
  message: string;

  ok: boolean;
  error: boolean;

  constructor(id: string, label: string, message: string) {
    this.id = id;
    this.label = label;
    this.message = message;
    this.ok = false;
    this.error = false;
  }
}
